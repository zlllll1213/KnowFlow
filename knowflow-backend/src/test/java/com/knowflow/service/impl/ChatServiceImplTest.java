package com.knowflow.service.impl;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.knowflow.common.BusinessException;
import com.knowflow.dto.ChatAskRequest;
import com.knowflow.entity.ChatMessage;
import com.knowflow.entity.ChatSession;
import com.knowflow.entity.KnowledgeBase;
import com.knowflow.mapper.ChatMessageMapper;
import com.knowflow.mapper.ChatSessionMapper;
import com.knowflow.service.RagCallLogService;
import com.knowflow.service.security.OwnershipChecker;
import com.knowflow.util.RagClient;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ChatServiceImplTest {

    private static final Long USER_ID = 7L;
    private static final Long KB_ID = 11L;
    private static final Long SESSION_ID = 13L;

    @Mock
    private ChatSessionMapper sessionMapper;
    @Mock
    private ChatMessageMapper messageMapper;
    @Mock
    private OwnershipChecker ownershipChecker;
    @Mock
    private RagClient ragClient;
    @Mock
    private RagCallLogService ragCallLogService;

    private ChatServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new ChatServiceImpl(
                sessionMapper,
                messageMapper,
                ownershipChecker,
                ragClient,
                ragCallLogService,
                new ObjectMapper(),
                Runnable::run
        );
    }

    @Test
    void askAgentStreamDoesNotSaveAssistantMessageWhenAnswerIsEmpty() {
        ChatAskRequest request = request("请总结知识库");
        when(ownershipChecker.requireKbOwner(USER_ID, KB_ID)).thenReturn(kb());
        when(sessionMapper.selectById(SESSION_ID)).thenReturn(session());
        doAnswer(invocation -> {
            RagClient.StreamListener listener = invocation.getArgument(3);
            listener.onDone();
            return null;
        }).when(ragClient).askAgentStream(eq(KB_ID), eq("请总结知识库"), eq(5), any());

        service.askAgentStream(USER_ID, request);

        ArgumentCaptor<ChatMessage> messageCaptor = ArgumentCaptor.forClass(ChatMessage.class);
        verify(messageMapper, times(1)).insert(messageCaptor.capture());
        assertThat(messageCaptor.getValue().getRole()).isEqualTo("user");
        verify(sessionMapper, never()).updateById(any());
        verify(ragCallLogService, never()).record(any(), any(), any(), any(), any(), any(), any(Integer.class),
                any(Integer.class), any(), any(), any());
    }

    @Test
    void askStreamRejectsRequestWhenTotalSseConnectionLimitIsReached() {
        ChatAskRequest request = request("请总结知识库");
        when(ownershipChecker.requireKbOwner(USER_ID, KB_ID)).thenReturn(kb());
        when(sessionMapper.selectById(SESSION_ID)).thenReturn(session());
        ReflectionTestUtils.setField(service, "maxTotalSseConnections", 0);

        assertThatThrownBy(() -> service.askStream(USER_ID, request))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("流式连接数过多");

        verify(messageMapper, never()).insert(any());
        verify(ragClient, never()).askStream(any(), any(), any(Integer.class), any());
    }

    private ChatAskRequest request(String question) {
        ChatAskRequest request = new ChatAskRequest();
        request.setKbId(KB_ID);
        request.setSessionId(SESSION_ID);
        request.setQuestion(question);
        return request;
    }

    private KnowledgeBase kb() {
        KnowledgeBase kb = new KnowledgeBase();
        kb.setId(KB_ID);
        kb.setUserId(USER_ID);
        return kb;
    }

    private ChatSession session() {
        ChatSession session = new ChatSession();
        session.setId(SESSION_ID);
        session.setKbId(KB_ID);
        session.setUserId(USER_ID);
        return session;
    }
}
