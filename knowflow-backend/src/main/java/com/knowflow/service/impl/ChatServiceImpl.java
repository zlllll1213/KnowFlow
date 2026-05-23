package com.knowflow.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.knowflow.common.BusinessException;
import com.knowflow.dto.ChatAskRequest;
import com.knowflow.dto.ChatSessionCreateRequest;
import com.knowflow.dto.RagResponse;
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

import java.util.Collections;
import java.util.List;
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

        // 校验会话归属
        ChatSession session = sessionMapper.selectById(request.getSessionId());
        if (session == null || !session.getUserId().equals(userId)) {
            throw new BusinessException(40030, "无权访问该会话");
        }

        // 保存用户问题
        ChatMessage userMsg = new ChatMessage();
        userMsg.setSessionId(request.getSessionId());
        userMsg.setKbId(request.getKbId());
        userMsg.setUserId(userId);
        userMsg.setRole("user");
        userMsg.setContent(request.getQuestion());
        userMsg.setSources("[]");
        messageMapper.insert(userMsg);

        // 调用 Go RAG Service（不可用时自动降级 mock）
        RagResponse ragResponse = ragClient.ask(request.getKbId(), request.getQuestion(), 5);
        String answer = ragResponse.getAnswer();
        String sourcesJson = serializeSources(ragResponse);

        // 保存助手回答
        ChatMessage assistantMsg = new ChatMessage();
        assistantMsg.setSessionId(request.getSessionId());
        assistantMsg.setKbId(request.getKbId());
        assistantMsg.setUserId(userId);
        assistantMsg.setRole("assistant");
        assistantMsg.setContent(answer);
        assistantMsg.setSources(sourcesJson);
        messageMapper.insert(assistantMsg);

        // 更新会话时间
        sessionMapper.updateById(session);

        return ChatMessageVO.builder()
                .id(assistantMsg.getId())
                .role(assistantMsg.getRole())
                .content(assistantMsg.getContent())
                .sources(ragResponse.getSources())
                .createdAt(assistantMsg.getCreatedAt())
                .build();
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
            return objectMapper.writeValueAsString(ragResponse.getSources());
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
}
