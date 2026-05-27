package com.knowflow.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.knowflow.entity.ChatSession;
import com.knowflow.entity.Document;
import com.knowflow.entity.KnowledgeBase;
import com.knowflow.mapper.ChatMessageMapper;
import com.knowflow.mapper.ChatSessionMapper;
import com.knowflow.mapper.DocumentMapper;
import com.knowflow.mapper.KnowledgeBaseMapper;
import com.knowflow.service.DashboardService;
import com.knowflow.vo.DashboardStatsVO;
import com.knowflow.vo.DocumentVO;
import com.knowflow.vo.RecentFailedTaskVO;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.sql.Timestamp;
import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class DashboardServiceImpl implements DashboardService {

    private final KnowledgeBaseMapper knowledgeBaseMapper;
    private final DocumentMapper documentMapper;
    private final ChatSessionMapper chatSessionMapper;
    private final ChatMessageMapper chatMessageMapper;
    private final JdbcTemplate jdbcTemplate;

    @Override
    public DashboardStatsVO getStats(Long userId) {
        return DashboardStatsVO.builder()
                .kbCount(countKnowledgeBases(userId))
                .docCount(countDocuments(userId, null))
                .doneDocCount(countDocuments(userId, "DONE"))
                .failedDocCount(countDocuments(userId, "FAILED"))
                .chunkCount(countChunks(userId))
                .chatCount(countUserQuestions(userId))
                .recentDocs(recentDocuments(userId))
                .recentSessions(recentSessions(userId))
                .recentFailedTasks(recentFailedTasks(userId))
                .build();
    }

    private long countKnowledgeBases(Long userId) {
        return knowledgeBaseMapper.selectCount(new LambdaQueryWrapper<KnowledgeBase>()
                .eq(KnowledgeBase::getUserId, userId));
    }

    private long countDocuments(Long userId, String status) {
        LambdaQueryWrapper<Document> wrapper = new LambdaQueryWrapper<Document>()
                .eq(Document::getUserId, userId);
        if (status != null) {
            wrapper.eq(Document::getStatus, status);
        }
        return documentMapper.selectCount(wrapper);
    }

    private long countUserQuestions(Long userId) {
        return chatMessageMapper.selectCount(new LambdaQueryWrapper<com.knowflow.entity.ChatMessage>()
                .eq(com.knowflow.entity.ChatMessage::getUserId, userId)
                .eq(com.knowflow.entity.ChatMessage::getRole, "user"));
    }

    private long countChunks(Long userId) {
        Long value = jdbcTemplate.queryForObject("""
                SELECT COUNT(*)
                FROM document_chunk dc
                JOIN document d ON d.id = dc.document_id
                JOIN knowledge_base kb ON kb.id = d.kb_id
                WHERE d.user_id = ?
                  AND kb.user_id = ?
                  AND d.is_deleted = 0
                  AND kb.is_deleted = 0
                """, Long.class, userId, userId);
        return value == null ? 0 : value;
    }

    private List<DocumentVO> recentDocuments(Long userId) {
        return documentMapper.selectList(new LambdaQueryWrapper<Document>()
                        .eq(Document::getUserId, userId)
                        .orderByDesc(Document::getCreatedAt)
                        .last("LIMIT 5"))
                .stream()
                .map(this::toDocumentVO)
                .collect(Collectors.toList());
    }

    private List<ChatSession> recentSessions(Long userId) {
        return chatSessionMapper.selectList(new LambdaQueryWrapper<ChatSession>()
                .eq(ChatSession::getUserId, userId)
                .orderByDesc(ChatSession::getUpdatedAt)
                .last("LIMIT 5"));
    }

    private List<RecentFailedTaskVO> recentFailedTasks(Long userId) {
        return jdbcTemplate.query("""
                        SELECT pt.id AS task_id,
                               d.id AS document_id,
                               d.file_name,
                               COALESCE(NULLIF(pt.error_message, ''), '未知错误') AS error_message,
                               pt.updated_at
                        FROM parse_task pt
                        JOIN document d ON d.id = pt.document_id
                        JOIN knowledge_base kb ON kb.id = d.kb_id
                        WHERE d.user_id = ?
                          AND kb.user_id = ?
                          AND pt.status = 'FAILED'
                          AND d.is_deleted = 0
                          AND kb.is_deleted = 0
                        ORDER BY pt.updated_at DESC
                        LIMIT 5
                        """,
                (rs, rowNum) -> RecentFailedTaskVO.builder()
                        .taskId(rs.getLong("task_id"))
                        .documentId(rs.getLong("document_id"))
                        .fileName(rs.getString("file_name"))
                        .errorMessage(rs.getString("error_message"))
                        .updatedAt(toLocalDateTime(rs.getTimestamp("updated_at")))
                        .build(),
                userId,
                userId);
    }

    private DocumentVO toDocumentVO(Document doc) {
        return DocumentVO.builder()
                .id(doc.getId())
                .kbId(doc.getKbId())
                .fileName(doc.getFileName())
                .fileType(doc.getFileType())
                .fileSize(doc.getFileSize())
                .status(doc.getStatus())
                .chunkCount(doc.getChunkCount())
                .errorMessage(doc.getErrorMessage())
                .createdAt(doc.getCreatedAt())
                .updatedAt(doc.getUpdatedAt())
                .build();
    }

    private java.time.LocalDateTime toLocalDateTime(Timestamp timestamp) {
        return timestamp == null ? null : timestamp.toLocalDateTime();
    }
}
