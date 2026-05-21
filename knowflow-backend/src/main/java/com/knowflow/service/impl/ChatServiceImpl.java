package com.knowflow.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.knowflow.common.BusinessException;
import com.knowflow.dto.ChatAskRequest;
import com.knowflow.dto.ChatSessionCreateRequest;
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
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class ChatServiceImpl implements ChatService {

    private final ChatSessionMapper sessionMapper;
    private final ChatMessageMapper messageMapper;
    private final KnowledgeBaseMapper kbMapper;
    private final RagClient ragClient;

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
        messageMapper.insert(userMsg);

        // 调用 RAG 获取回答（当前为模拟）
        String answer = ragClient.mockAsk(request.getQuestion(), request.getKbId());

        // 保存助手回答
        ChatMessage assistantMsg = new ChatMessage();
        assistantMsg.setSessionId(request.getSessionId());
        assistantMsg.setKbId(request.getKbId());
        assistantMsg.setUserId(userId);
        assistantMsg.setRole("assistant");
        assistantMsg.setContent(answer);
        messageMapper.insert(assistantMsg);

        // 更新会话时间
        sessionMapper.updateById(session);

        return ChatMessageVO.builder()
                .id(assistantMsg.getId())
                .role(assistantMsg.getRole())
                .content(assistantMsg.getContent())
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
                        .createdAt(m.getCreatedAt())
                        .build())
                .collect(Collectors.toList());
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
