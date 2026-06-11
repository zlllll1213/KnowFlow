package com.knowflow.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.knowflow.common.BusinessException;
import com.knowflow.common.PageResult;
import com.knowflow.dto.AgentResponse;
import com.knowflow.dto.AgentTraceStep;
import com.knowflow.dto.ChatAskRequest;
import com.knowflow.dto.ChatSessionCreateRequest;
import com.knowflow.dto.RagResponse;
import com.knowflow.dto.RagSourceChunk;
import com.knowflow.entity.ChatMessage;
import com.knowflow.entity.ChatSession;
import com.knowflow.entity.KnowledgeBase;
import com.knowflow.mapper.ChatMessageMapper;
import com.knowflow.mapper.ChatSessionMapper;
import com.knowflow.mapper.KnowledgeBaseMapper;
import com.knowflow.service.ChatService;
import com.knowflow.util.RagClient;
import com.knowflow.vo.ChatMessageVO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.Collections;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.stream.Collectors;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Map;

@Slf4j
@Service
@RequiredArgsConstructor
public class ChatServiceImpl implements ChatService {

    private final ChatSessionMapper sessionMapper;
    private final ChatMessageMapper messageMapper;
    private final KnowledgeBaseMapper kbMapper;
    private final RagClient ragClient;
    private final ObjectMapper objectMapper;

    @Override
    public ChatSession createSession(Long userId, ChatSessionCreateRequest request) {
        checkKbOwnership(userId, request.getKbId());

        ChatSession session = new ChatSession();
        session.setKbId(request.getKbId());
        session.setUserId(userId);
        session.setTitle(request.getTitle() != null ? request.getTitle() : "New Chat");
        sessionMapper.insert(session);
        return session;
    }

    @Override
    public PageResult<ChatSession> listSessions(Long userId, Long kbId, long page, long size) {
        checkKbOwnership(userId, kbId);

        Page<ChatSession> result = sessionMapper.selectPage(new Page<>(page, size),
                new LambdaQueryWrapper<ChatSession>()
                        .eq(ChatSession::getKbId, kbId)
                        .eq(ChatSession::getUserId, userId)
                        .orderByDesc(ChatSession::getUpdatedAt));
        return PageResult.of(result.getTotal(), result.getCurrent(), result.getSize(), result.getRecords());
    }

    @Override
    public ChatMessageVO ask(Long userId, ChatAskRequest request) {
        checkKbOwnership(userId, request.getKbId());

        ChatSession session = checkSessionOwnership(userId, request.getSessionId(), request.getKbId());
        saveMessage(userId, request.getSessionId(), request.getKbId(), "user", request.getQuestion(), Collections.emptyList());

        // 调用 Go RAG Service；默认 fail-closed，只有显式开发配置才启用 mock。
        RagResponse ragResponse = ragClient.ask(request.getKbId(), request.getQuestion(), 5);
        String answer = ragResponse.getAnswer();
        List<RagSourceChunk> sources = ragResponse.getSources() == null
                ? Collections.emptyList()
                : ragResponse.getSources();

        // 保存助手回答
        ChatMessage assistantMsg = saveMessage(userId, request.getSessionId(), request.getKbId(), "assistant", answer, sources);

        // 更新会话时间
        touchSession(session);

        return ChatMessageVO.builder()
                .id(assistantMsg.getId())
                .role(assistantMsg.getRole())
                .content(assistantMsg.getContent())
                .sources(sources)
                .createdAt(assistantMsg.getCreatedAt())
                .build();
    }

    @Override
    public SseEmitter askStream(Long userId, ChatAskRequest request) {
        checkKbOwnership(userId, request.getKbId());
        ChatSession session = checkSessionOwnership(userId, request.getSessionId(), request.getKbId());
        saveMessage(userId, request.getSessionId(), request.getKbId(), "user", request.getQuestion(), Collections.emptyList());

        SseEmitter emitter = new SseEmitter(120_000L);
        emitter.onTimeout(() -> log.warn("流式问答超时: userId={}, sessionId={}", userId, request.getSessionId()));
        emitter.onError(e -> log.warn("流式问答连接异常: userId={}, sessionId={}, error={}",
                userId, request.getSessionId(), e.getMessage()));
        CompletableFuture.runAsync(() -> {
            StringBuilder answer = new StringBuilder();
            ListHolder sourcesHolder = new ListHolder();
            AtomicBoolean completed = new AtomicBoolean(false);

            ragClient.askStream(request.getKbId(), request.getQuestion(), 5, new RagClient.StreamListener() {
                @Override
                public void onToken(String token) {
                    if (token == null || token.isEmpty()) {
                        return;
                    }
                    answer.append(token);
                    if (!sendSse(emitter, "token", Collections.singletonMap("content", token))) {
                        completed.set(true);
                    }
                }

                @Override
                public void onSources(List<RagSourceChunk> sources) {
                    sourcesHolder.sources = sources == null ? Collections.emptyList() : sources;
                    sendSse(emitter, "sources", sourcesHolder.sources);
                }

                @Override
                public void onError(String message) {
                    if (completed.compareAndSet(false, true)) {
                        sendSse(emitter, "error", Collections.singletonMap("message", message));
                        emitter.complete();
                    }
                }

                @Override
                public void onDone() {
                    if (!completed.compareAndSet(false, true)) {
                        return;
                    }
                    if (answer.isEmpty()) {
                        sendSse(emitter, "error", Collections.singletonMap("message", "RAG 服务未返回回答"));
                        emitter.complete();
                        return;
                    }
                    ChatMessage assistantMsg = saveMessage(userId, request.getSessionId(), request.getKbId(),
                            "assistant", answer.toString(), sourcesHolder.sources);
                    touchSession(session);
                    sendSse(emitter, "done", ChatMessageVO.builder()
                            .id(assistantMsg.getId())
                            .role(assistantMsg.getRole())
                            .content(assistantMsg.getContent())
                            .sources(sourcesHolder.sources)
                            .createdAt(assistantMsg.getCreatedAt())
                            .build());
                    emitter.complete();
                }
            });
        }).exceptionally(e -> {
            log.warn("流式问答失败", e);
            sendSse(emitter, "error", Collections.singletonMap("message", e.getMessage()));
            emitter.complete();
            return null;
        });
        return emitter;
    }

    @Override
    public AgentResponse askAgent(Long userId, ChatAskRequest request) {
        checkKbOwnership(userId, request.getKbId());
        ChatSession session = checkSessionOwnership(userId, request.getSessionId(), request.getKbId());
        saveMessage(userId, request.getSessionId(), request.getKbId(), "user", request.getQuestion(), Collections.emptyList());

        AgentResponse response = ragClient.askAgent(request.getKbId(), request.getQuestion(), 5);
        List<RagSourceChunk> sources = response.getSources() == null ? Collections.emptyList() : response.getSources();
        saveMessage(userId, request.getSessionId(), request.getKbId(), "assistant", response.getAnswer(), sources);
        touchSession(session);
        response.setSources(sources);
        return response;
    }

    @Override
    public SseEmitter askAgentStream(Long userId, ChatAskRequest request) {
        checkKbOwnership(userId, request.getKbId());
        ChatSession session = checkSessionOwnership(userId, request.getSessionId(), request.getKbId());
        saveMessage(userId, request.getSessionId(), request.getKbId(), "user", request.getQuestion(), Collections.emptyList());

        SseEmitter emitter = new SseEmitter(120_000L);
        CompletableFuture.runAsync(() -> {
            StringBuilder answer = new StringBuilder();
            ListHolder sourcesHolder = new ListHolder();
            AgentMetaHolder meta = new AgentMetaHolder();
            AtomicBoolean completed = new AtomicBoolean(false);

            ragClient.askAgentStream(request.getKbId(), request.getQuestion(), 5, new RagClient.StreamListener() {
                @Override
                public void onToken(String token) {
                    if (token == null || token.isEmpty()) {
                        return;
                    }
                    answer.append(token);
                    if (!sendSse(emitter, "token", Collections.singletonMap("content", token))) {
                        completed.set(true);
                    }
                }

                @Override
                public void onSources(List<RagSourceChunk> sources) {
                    sourcesHolder.sources = sources == null ? Collections.emptyList() : sources;
                    sendSse(emitter, "sources", sourcesHolder.sources);
                }

                @Override
                public void onMeta(Map<String, Object> metaEvent) {
                    meta.intent = String.valueOf(metaEvent.getOrDefault("intent", "qa"));
                    Object confidence = metaEvent.get("confidence");
                    if (confidence instanceof Number number) {
                        meta.confidence = number.doubleValue();
                    }
                    Object trace = metaEvent.get("trace");
                    if (trace != null) {
                        meta.trace = objectMapper.convertValue(trace,
                                objectMapper.getTypeFactory().constructCollectionType(List.class, AgentTraceStep.class));
                    }
                    Object latencyMs = metaEvent.get("latencyMs");
                    if (latencyMs instanceof Number number) {
                        meta.latencyMs = number.longValue();
                    }
                    sendSse(emitter, "meta", metaEvent);
                }

                @Override
                public void onError(String message) {
                    if (completed.compareAndSet(false, true)) {
                        sendSse(emitter, "error", Collections.singletonMap("message", message));
                        emitter.complete();
                    }
                }

                @Override
                public void onDone() {
                    if (!completed.compareAndSet(false, true)) {
                        return;
                    }
                    saveMessage(userId, request.getSessionId(), request.getKbId(), "assistant",
                            answer.toString(), sourcesHolder.sources);
                    touchSession(session);
                    sendSse(emitter, "done", new AgentResponse(meta.intent, answer.toString(),
                            sourcesHolder.sources, meta.confidence, meta.trace, meta.latencyMs));
                    emitter.complete();
                }
            });
        }).exceptionally(e -> {
            log.warn("Agent 流式问答失败", e);
            sendSse(emitter, "error", Collections.singletonMap("message", "Agent 问答失败"));
            emitter.complete();
            return null;
        });
        return emitter;
    }

    @Override
    public PageResult<ChatMessageVO> getHistory(Long userId, Long sessionId, long page, long size) {
        ChatSession session = sessionMapper.selectById(sessionId);
        if (session == null || !session.getUserId().equals(userId)) {
            throw new BusinessException(40030, "无权访问该会话");
        }

        Page<ChatMessage> result = messageMapper.selectPage(new Page<>(page, size),
                new LambdaQueryWrapper<ChatMessage>()
                        .eq(ChatMessage::getSessionId, sessionId)
                        .orderByAsc(ChatMessage::getCreatedAt));

        List<ChatMessageVO> records = result.getRecords().stream()
                .map(m -> ChatMessageVO.builder()
                        .id(m.getId())
                        .role(m.getRole())
                        .content(m.getContent())
                        .sources(deserializeSources(m.getSources()))
                        .createdAt(m.getCreatedAt())
                        .build())
                .collect(Collectors.toList());
        return PageResult.of(result.getTotal(), result.getCurrent(), result.getSize(), records);
    }

    // ---------- 序列化辅助 ----------

    @SuppressWarnings("unchecked")
    private List<com.knowflow.dto.RagSourceChunk> deserializeSources(String sourcesJson) {
        if (sourcesJson == null || sourcesJson.isEmpty()) {
            return Collections.emptyList();
        }
        try {
            return objectMapper.readValue(sourcesJson,
                    objectMapper.getTypeFactory().constructCollectionType(List.class,
                            com.knowflow.dto.RagSourceChunk.class));
        } catch (JsonProcessingException e) {
            log.warn("sources 反序列化失败", e);
            return Collections.emptyList();
        }
    }

    private void checkKbOwnership(Long userId, Long kbId) {
        KnowledgeBase kb = kbMapper.selectById(kbId);
        if (kb == null) {
            throw new BusinessException(40020, "知识库不存在");
        }
        if (!kb.getUserId().equals(userId)) {
            throw new BusinessException(40030, "无权访问该知识库");
        }
    }

    private ChatSession checkSessionOwnership(Long userId, Long sessionId, Long kbId) {
        ChatSession session = sessionMapper.selectById(sessionId);
        if (session == null || !session.getUserId().equals(userId) || !session.getKbId().equals(kbId)) {
            throw new BusinessException(40030, "无权访问该会话");
        }
        return session;
    }

    private ChatMessage saveMessage(Long userId, Long sessionId, Long kbId, String role, String content,
                                    List<RagSourceChunk> sources) {
        ChatMessage msg = new ChatMessage();
        msg.setSessionId(sessionId);
        msg.setKbId(kbId);
        msg.setUserId(userId);
        msg.setRole(role);
        msg.setContent(content);
        try {
            msg.setSources(objectMapper.writeValueAsString(sources == null ? Collections.emptyList() : sources));
        } catch (JsonProcessingException e) {
            msg.setSources("[]");
        }
        messageMapper.insert(msg);
        return msg;
    }

    private boolean sendSse(SseEmitter emitter, String event, Object data) {
        try {
            emitter.send(SseEmitter.event().name(event).data(data));
            return true;
        } catch (Exception e) {
            log.warn("SSE 发送失败: event={}, error={}", event, e.getMessage());
            return false;
        }
    }

    private void touchSession(ChatSession session) {
        session.setUpdatedAt(LocalDateTime.now());
        sessionMapper.updateById(session);
    }

    private static class ListHolder {
        private List<RagSourceChunk> sources = Collections.emptyList();
    }

    private static class AgentMetaHolder {
        private String intent = "qa";
        private double confidence = 0.0;
        private List<AgentTraceStep> trace = new ArrayList<>();
        private Long latencyMs = 0L;
    }
}
