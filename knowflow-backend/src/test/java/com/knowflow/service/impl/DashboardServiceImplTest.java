package com.knowflow.service.impl;

import com.knowflow.entity.ChatSession;
import com.knowflow.entity.Document;
import com.knowflow.mapper.ChatMessageMapper;
import com.knowflow.mapper.ChatSessionMapper;
import com.knowflow.mapper.DocumentMapper;
import com.knowflow.mapper.KnowledgeBaseMapper;
import com.knowflow.mapper.ParseTaskMapper;
import com.knowflow.vo.DashboardStatsVO;
import com.knowflow.vo.RecentFailedTaskVO;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class DashboardServiceImplTest {

    private static final Long USER_ID = 7L;

    @Mock
    private KnowledgeBaseMapper knowledgeBaseMapper;
    @Mock
    private DocumentMapper documentMapper;
    @Mock
    private ChatMessageMapper chatMessageMapper;
    @Mock
    private ChatSessionMapper chatSessionMapper;
    @Mock
    private ParseTaskMapper parseTaskMapper;

    private DashboardServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new DashboardServiceImpl(
                knowledgeBaseMapper,
                documentMapper,
                chatMessageMapper,
                chatSessionMapper,
                parseTaskMapper
        );
    }

    @Test
    void getStatsAggregatesDashboardDataWithinCurrentUserScope() {
        Document recentDoc = new Document();
        recentDoc.setId(101L);
        recentDoc.setKbId(201L);
        recentDoc.setFileName("recent.pdf");
        recentDoc.setFileType("pdf");
        recentDoc.setFileSize(4096L);
        recentDoc.setStatus("DONE");
        recentDoc.setChunkCount(12);
        recentDoc.setCreatedAt(LocalDateTime.now().minusHours(1));

        ChatSession recentSession = new ChatSession();
        recentSession.setId(301L);
        recentSession.setUserId(USER_ID);
        recentSession.setTitle("recent chat");

        RecentFailedTaskVO failedTask = RecentFailedTaskVO.builder()
                .taskId(401L)
                .documentId(101L)
                .fileName("failed.docx")
                .errorMessage("parse failed")
                .updatedAt(LocalDateTime.now())
                .build();

        when(knowledgeBaseMapper.selectCount(any())).thenReturn(3L);
        when(documentMapper.selectCount(any())).thenReturn(12L, 10L, 1L);
        when(chatMessageMapper.selectCount(any())).thenReturn(25L);
        when(documentMapper.countChunksByUser(USER_ID)).thenReturn(860L);
        when(documentMapper.selectList(any())).thenReturn(List.of(recentDoc));
        when(chatSessionMapper.selectRecentByUser(USER_ID, 5)).thenReturn(List.of(recentSession));
        when(parseTaskMapper.selectRecentFailedByUser(USER_ID, 5)).thenReturn(List.of(failedTask));

        DashboardStatsVO stats = service.getStats(USER_ID);

        assertThat(stats.getKbCount()).isEqualTo(3L);
        assertThat(stats.getDocCount()).isEqualTo(12L);
        assertThat(stats.getDoneDocCount()).isEqualTo(10L);
        assertThat(stats.getFailedDocCount()).isEqualTo(1L);
        assertThat(stats.getChunkCount()).isEqualTo(860L);
        assertThat(stats.getChatCount()).isEqualTo(25L);
        assertThat(stats.getRecentDocs()).hasSize(1);
        assertThat(stats.getRecentDocs().get(0).getFileName()).isEqualTo("recent.pdf");
        assertThat(stats.getRecentSessions()).containsExactly(recentSession);
        assertThat(stats.getRecentFailedTasks()).containsExactly(failedTask);

        assertSelectCountScopedToCurrentUser();
        assertRecentListsScopedToCurrentUser();
        assertMapperAggregatesScopedToCurrentUser();
    }

    @Test
    void getStatsReturnsZeroChunkCountWhenJdbcCountIsNull() {
        when(knowledgeBaseMapper.selectCount(any())).thenReturn(0L);
        when(documentMapper.selectCount(any())).thenReturn(0L, 0L, 0L);
        when(chatMessageMapper.selectCount(any())).thenReturn(0L);
        when(documentMapper.countChunksByUser(USER_ID)).thenReturn(null);
        when(documentMapper.selectList(any())).thenReturn(List.of());
        when(chatSessionMapper.selectRecentByUser(USER_ID, 5)).thenReturn(List.of());
        when(parseTaskMapper.selectRecentFailedByUser(USER_ID, 5)).thenReturn(List.of());

        DashboardStatsVO stats = service.getStats(USER_ID);

        assertThat(stats.getChunkCount()).isZero();
    }

    private void assertSelectCountScopedToCurrentUser() {
        verify(knowledgeBaseMapper).selectCount(any());
        verify(documentMapper, times(3)).selectCount(any());
        verify(chatMessageMapper).selectCount(any());
    }

    private void assertRecentListsScopedToCurrentUser() {
        verify(documentMapper).selectList(any());
    }

    private void assertMapperAggregatesScopedToCurrentUser() {
        verify(documentMapper).countChunksByUser(USER_ID);
        verify(chatSessionMapper).selectRecentByUser(USER_ID, 5);
        verify(parseTaskMapper).selectRecentFailedByUser(USER_ID, 5);
    }
}
