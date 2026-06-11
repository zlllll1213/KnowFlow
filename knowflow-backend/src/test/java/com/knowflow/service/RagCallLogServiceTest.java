package com.knowflow.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.util.ReflectionTestUtils;

import java.sql.Timestamp;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class RagCallLogServiceTest {

    private JdbcTemplate jdbcTemplate;
    private RagCallLogService service;

    @BeforeEach
    void setUp() {
        jdbcTemplate = mock(JdbcTemplate.class);
        service = new RagCallLogService(jdbcTemplate, new ObjectMapper());
    }

    @Test
    void cleanupExpiredLogsDeletesRowsOlderThanRetentionWindow() {
        ReflectionTestUtils.setField(service, "retentionDays", 30);
        when(jdbcTemplate.update(anyString(), any(Timestamp.class))).thenReturn(7);

        int deleted = service.cleanupExpiredLogs();

        assertThat(deleted).isEqualTo(7);
        verify(jdbcTemplate).update(contains("DELETE FROM rag_call_log WHERE created_at < ?"),
                any(Timestamp.class));
    }

    @Test
    void cleanupExpiredLogsIsDisabledWhenRetentionIsNotPositive() {
        ReflectionTestUtils.setField(service, "retentionDays", 0);

        int deleted = service.cleanupExpiredLogs();

        assertThat(deleted).isZero();
        verify(jdbcTemplate, never()).update(anyString(), any(Timestamp.class));
    }
}
