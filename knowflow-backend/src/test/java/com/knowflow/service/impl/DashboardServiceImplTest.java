package com.knowflow.service.impl;

import com.knowflow.entity.ChatSession;
import com.knowflow.entity.Document;
import com.knowflow.mapper.ChatMessageMapper;
import com.knowflow.mapper.ChatSessionMapper;
import com.knowflow.mapper.DocumentMapper;
import com.knowflow.mapper.KnowledgeBaseMapper;
import com.knowflow.vo.DashboardStatsVO;
import com.knowflow.vo.RecentFailedTaskVO;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;

import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class DashboardServiceImplTest {

    private static final Long USER_ID = 7L;

    @Mock
    private KnowledgeBaseMapper knowledgeBaseMapper;
    @Mock
    private DocumentMapper documentMapper;
    @Mock
    private ChatSessionMapper chatSessionMapper;
    @Mock
    private ChatMessageMapper chatMessageMapper;

    private DashboardServiceImpl service;
    private RecordingJdbcTemplate jdbcTemplate;

    @org.junit.jupiter.api.BeforeEach
    void setUp() {
        jdbcTemplate = new RecordingJdbcTemplate();
        service = new DashboardServiceImpl(
                knowledgeBaseMapper,
                documentMapper,
                chatSessionMapper,
                chatMessageMapper,
                jdbcTemplate
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
        jdbcTemplate.chunkCount = 860L;
        when(documentMapper.selectList(any())).thenReturn(List.of(recentDoc));
        when(chatSessionMapper.selectList(any())).thenReturn(List.of(recentSession));
        jdbcTemplate.failedTasks = List.of(failedTask);

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
        assertJdbcSqlScopedToCurrentUser();
    }

    @Test
    void getStatsReturnsZeroChunkCountWhenJdbcCountIsNull() {
        when(knowledgeBaseMapper.selectCount(any())).thenReturn(0L);
        when(documentMapper.selectCount(any())).thenReturn(0L, 0L, 0L);
        when(chatMessageMapper.selectCount(any())).thenReturn(0L);
        jdbcTemplate.chunkCount = null;
        when(documentMapper.selectList(any())).thenReturn(List.of());
        when(chatSessionMapper.selectList(any())).thenReturn(List.of());
        jdbcTemplate.failedTasks = List.of();

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
        verify(chatSessionMapper).selectList(any());
    }

    private void assertJdbcSqlScopedToCurrentUser() {
        assertThat(jdbcTemplate.chunkSql).contains("d.user_id = ?", "kb.user_id = ?");
        assertThat(jdbcTemplate.chunkArgs).containsExactly(USER_ID, USER_ID);

        assertThat(jdbcTemplate.failedTaskSql).contains("d.user_id = ?", "kb.user_id = ?", "pt.status = 'FAILED'");
        assertThat(jdbcTemplate.failedTaskArgs).containsExactly(USER_ID, USER_ID);
    }

    private static class RecordingJdbcTemplate extends JdbcTemplate {
        private Long chunkCount;
        private List<RecentFailedTaskVO> failedTasks = List.of();
        private String chunkSql;
        private List<Object> chunkArgs = List.of();
        private String failedTaskSql;
        private List<Object> failedTaskArgs = List.of();

        @Override
        public <T> T queryForObject(String sql, Class<T> requiredType, Object... args) {
            this.chunkSql = sql;
            this.chunkArgs = Arrays.asList(args);
            return requiredType.cast(chunkCount);
        }

        @Override
        @SuppressWarnings("unchecked")
        public <T> List<T> query(String sql, RowMapper<T> rowMapper, Object... args) {
            this.failedTaskSql = sql;
            this.failedTaskArgs = Arrays.asList(args);
            return (List<T>) failedTasks;
        }
    }
}
