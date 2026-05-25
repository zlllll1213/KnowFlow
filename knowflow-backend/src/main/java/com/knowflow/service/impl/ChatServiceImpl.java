package com.knowflow.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.knowflow.common.BusinessException;
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
    public List<ChatSession> listSessions(Long userId, Long kbId) {
        checkKbOwnership(userId, kbId);

        return sessionMapper.selectList(
                new LambdaQueryWrapper<ChatSession>()
                        .eq(ChatSession::getKbId, kbId)
                        .eq(ChatSession::getUserId, userId)
                        .orderByDesc(ChatSession::getUpdatedAt));
    }

    @Override
    public ChatMessageVO ask(Long userId, ChatAskRequest request) {
        checkKbOwnership(userId, request.getKbId());

        ChatSession session = checkSessionOwnership(userId, request.getSessionId(), request.getKbId());
        saveMessage(userId, request.getSessionId(), request.getKbId(), "user", request.getQuestion(), Collections.emptyList());

        // 调用 Go RAG Service（不可用时自动降级 mock）
        RagResponse ragResponse = ragClient.ask(request.getKbId(), request.getQuestion(), 5);
        String answer = ragResponse.getAnswer();
        List<RagSourceChunk> sources = ragResponse.getSources() == null
                ? Collections.emptyList()
                : ragResponse.getSources();

        // 保存助手回答
        ChatMessage assistantMsg = saveMessage(userId, request.getSessionId(), request.getKbId(), "assistant", answer, sources);

        // 更新会话时间
        sessionMapper.updateById(session);

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
                    sendSse(emitter, "token", Collections.singletonMap("content", token));
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
                    ChatMessage assistantMsg = saveMessage(userId, request.getSessionId(), request.getKbId(),
                            "assistant", answer.toString(), sourcesHolder.sources);
                    sessionMapper.updateById(session);
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
    public List<ChatMessageVO> getHistory(Long userId, Long sessionId) {
        ChatSession session = sessionMapper.selectById(sessionId);
        if (session == null || !session.getUserId().equals(userId)) {
            throw new BusinessException(40030, "无权访问该会话");
        }

        List<ChatMessage> messages = messageMapper.selectList(
                new LambdaQueryWrapper<ChatMessage>()
                        .eq(ChatMessage::getSessionId, sessionId)
                        .orderByAsc(ChatMessage::getCreatedAt));

        return messages.stream()
                .map(m -> ChatMessageVO.builder()
                        .id(m.getId())
                        .role(m.getRole())
                        .content(m.getContent())
                        .sources(deserializeSources(m.getSources()))
                        .createdAt(m.getCreatedAt())
                        .build())
                .collect(Collectors.toList());
    }

    // ---------- 序列化辅助 ----------

    private String serializeSources(RagResponse ragResponse) {
        try {
            return objectMapper.writeValueAsString(ragResponse.getSources() == null
                    ? Collections.emptyList()
                    : ragResponse.getSources());
        } catch (JsonProcessingException e) {
            log.warn("sources 序列化失败", e);
            return "[]";
        }
    }

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

    private void sendSse(SseEmitter emitter, String event, Object data) {
        try {
            emitter.send(SseEmitter.event().name(event).data(data));
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    private static class ListHolder {
        private List<RagSourceChunk> sources = Collections.emptyList();
    }
}
