package com.knowflow.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.knowflow.entity.ChatSession;
import com.knowflow.entity.Document;
import com.knowflow.entity.KnowledgeBase;
import com.knowflow.mapper.ChatMessageMapper;
import com.knowflow.mapper.ChatSessionMapper;
import com.knowflow.mapper.DocumentMapper;
import com.knowflow.mapper.KnowledgeBaseMapper;
import com.knowflow.mapper.ParseTaskMapper;
import com.knowflow.service.DashboardService;
import com.knowflow.vo.DashboardStatsVO;
import com.knowflow.vo.DocumentVO;
import com.knowflow.vo.RecentFailedTaskVO;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class DashboardServiceImpl implements DashboardService {

    private final KnowledgeBaseMapper knowledgeBaseMapper;
    private final DocumentMapper documentMapper;
    private final ChatMessageMapper chatMessageMapper;
    private final ChatSessionMapper chatSessionMapper;
    private final ParseTaskMapper parseTaskMapper;

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
        Long value = documentMapper.countChunksByUser(userId);
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
        return chatSessionMapper.selectRecentByUser(userId, 5);
    }

    private List<RecentFailedTaskVO> recentFailedTasks(Long userId) {
        return parseTaskMapper.selectRecentFailedByUser(userId, 5);
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
}
