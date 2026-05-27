package com.knowflow.controller;

import com.knowflow.common.Result;
import com.knowflow.security.LoginUser;
import com.knowflow.vo.DashboardStatsVO;
import com.knowflow.vo.DocumentVO;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/dashboard")
@RequiredArgsConstructor
public class DashboardController {

    private final JdbcTemplate jdbcTemplate;

    @GetMapping("/stats")
    public Result<DashboardStatsVO> stats(@AuthenticationPrincipal LoginUser loginUser) {
        Long userId = loginUser.getUserId();
        DashboardStatsVO stats = DashboardStatsVO.builder()
                .knowledgeBaseCount(count("SELECT COUNT(*) FROM knowledge_base WHERE user_id = ? AND is_deleted = 0", userId))
                .documentCount(count("SELECT COUNT(*) FROM document WHERE user_id = ? AND is_deleted = 0", userId))
                .parsedDocumentCount(count("SELECT COUNT(*) FROM document WHERE user_id = ? AND is_deleted = 0 AND status = 'DONE'", userId))
                .chunkCount(count("""
                        SELECT COUNT(*)
                        FROM document_chunk dc
                        JOIN document d ON d.id = dc.document_id
                        WHERE d.user_id = ? AND d.is_deleted = 0
                        """, userId))
                .sessionCount(count("SELECT COUNT(*) FROM chat_session WHERE user_id = ? AND is_deleted = 0", userId))
                .questionCount(count("SELECT COUNT(*) FROM chat_message WHERE user_id = ? AND role = 'user'", userId))
                .recentDocuments(recentDocuments(userId))
                .recentFailedTasks(recentFailedTasks(userId))
                .build();
        return Result.success(stats);
    }

    private long count(String sql, Long userId) {
        Long value = jdbcTemplate.queryForObject(sql, Long.class, userId);
        return value == null ? 0 : value;
    }

    private List<DocumentVO> recentDocuments(Long userId) {
        return jdbcTemplate.query("""
                        SELECT id, kb_id, file_name, file_type, file_size, status, chunk_count, error_message, created_at, updated_at
                        FROM document
                        WHERE user_id = ? AND is_deleted = 0
                        ORDER BY created_at DESC
                        LIMIT 5
                        """,
                (rs, rowNum) -> DocumentVO.builder()
                        .id(rs.getLong("id"))
                        .kbId(rs.getLong("kb_id"))
                        .fileName(rs.getString("file_name"))
                        .fileType(rs.getString("file_type"))
                        .fileSize(rs.getLong("file_size"))
                        .status(rs.getString("status"))
                        .chunkCount(rs.getInt("chunk_count"))
                        .errorMessage(rs.getString("error_message"))
                        .createdAt(rs.getTimestamp("created_at").toLocalDateTime())
                        .updatedAt(rs.getTimestamp("updated_at").toLocalDateTime())
                        .build(),
                userId);
    }

    private List<String> recentFailedTasks(Long userId) {
        return jdbcTemplate.queryForList("""
                        SELECT CONCAT(d.file_name, ': ', COALESCE(NULLIF(pt.error_message, ''), '未知错误'))
                        FROM parse_task pt
                        JOIN document d ON d.id = pt.document_id
                        WHERE d.user_id = ? AND pt.status = 'FAILED' AND d.is_deleted = 0
                        ORDER BY pt.updated_at DESC
                        LIMIT 5
                        """, String.class, userId);
    }
}
