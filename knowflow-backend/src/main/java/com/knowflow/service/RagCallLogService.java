package com.knowflow.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.knowflow.dto.AgentTraceStep;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import java.util.Collections;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class RagCallLogService {

    private final JdbcTemplate jdbcTemplate;
    private final ObjectMapper objectMapper;

    @Value("${knowflow.rag-call-log.retention-days:90}")
    private int retentionDays;

    public void record(Long kbId,
                       Long userId,
                       Long sessionId,
                       String mode,
                       String intent,
                       String question,
                       int topK,
                       int sourceCount,
                       Long totalLatencyMs,
                       Double confidence,
                       List<AgentTraceStep> trace) {
        try {
            jdbcTemplate.update("""
                    INSERT INTO rag_call_log (
                        kb_id, user_id, session_id, mode, intent, question_summary,
                        top_k, source_count, total_latency_ms, confidence, trace
                    ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?::jsonb)
                    """,
                    kbId,
                    userId,
                    sessionId,
                    safeMode(mode),
                    intent == null ? "" : intent,
                    summarize(question),
                    topK,
                    Math.max(0, sourceCount),
                    totalLatencyMs == null ? 0L : Math.max(0L, totalLatencyMs),
                    confidence == null ? 0.0 : confidence,
                    traceJson(trace));
        } catch (RuntimeException e) {
            log.warn("RAG 调用日志写入失败: kbId={}, userId={}, sessionId={}", kbId, userId, sessionId, e);
        }
    }

    @Scheduled(cron = "${knowflow.rag-call-log.cleanup-cron:0 30 3 * * *}")
    public void cleanupExpiredLogsScheduled() {
        cleanupExpiredLogs();
    }

    public int cleanupExpiredLogs() {
        if (retentionDays <= 0) {
            log.debug("RAG 调用日志清理已禁用: retentionDays={}", retentionDays);
            return 0;
        }

        Timestamp cutoff = Timestamp.from(Instant.now().minus(Duration.ofDays(retentionDays)));
        try {
            return jdbcTemplate.update("DELETE FROM rag_call_log WHERE created_at < ?", cutoff);
        } catch (RuntimeException e) {
            // 审计日志清理不能影响问答主链路，失败时保留数据并等待下一次调度。
            log.warn("RAG 调用日志清理失败: retentionDays={}", retentionDays, e);
            return 0;
        }
    }

    private String safeMode(String mode) {
        return mode == null || mode.isBlank() ? "rag" : mode;
    }

    private String summarize(String question) {
        String text = question == null ? "" : question.trim().replaceAll("\\s+", " ");
        if (text.length() <= 512) {
            return text;
        }
        return text.substring(0, 512);
    }

    private String traceJson(List<AgentTraceStep> trace) {
        try {
            return objectMapper.writeValueAsString(trace == null ? Collections.emptyList() : trace);
        } catch (JsonProcessingException e) {
            return "[]";
        }
    }
}
