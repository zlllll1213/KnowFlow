package com.knowflow.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.knowflow.common.BusinessException;
import com.knowflow.common.PageResult;
import com.knowflow.dto.KbCreateRequest;
import com.knowflow.dto.KbUpdateRequest;
import com.knowflow.entity.ChatMessage;
import com.knowflow.entity.ChatSession;
import com.knowflow.entity.Document;
import com.knowflow.entity.KnowledgeBase;
import com.knowflow.mapper.ChatMessageMapper;
import com.knowflow.mapper.ChatSessionMapper;
import com.knowflow.mapper.DocumentMapper;
import com.knowflow.mapper.KnowledgeBaseMapper;
import com.knowflow.service.DocumentService;
import com.knowflow.service.KnowledgeBaseService;
import com.knowflow.vo.KbVO;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class KnowledgeBaseServiceImpl implements KnowledgeBaseService {

    private final KnowledgeBaseMapper kbMapper;
    private final DocumentMapper documentMapper;
    private final DocumentService documentService;
    private final ChatSessionMapper chatSessionMapper;
    private final ChatMessageMapper chatMessageMapper;

    @Override
    public KbVO create(Long userId, KbCreateRequest request) {
        KnowledgeBase kb = new KnowledgeBase();
        kb.setUserId(userId);
        kb.setName(request.getName());
        kb.setDescription(request.getDescription());
        kbMapper.insert(kb);
        return toVO(kb);
    }

    @Override
    public PageResult<KbVO> list(Long userId, long page, long size) {
        Page<KnowledgeBase> result = kbMapper.selectPage(new Page<>(page, size),
                new LambdaQueryWrapper<KnowledgeBase>()
                        .eq(KnowledgeBase::getUserId, userId)
                        .orderByDesc(KnowledgeBase::getUpdatedAt));
        List<KbVO> records = result.getRecords().stream().map(this::toVO).collect(Collectors.toList());
        return PageResult.of(result.getTotal(), result.getCurrent(), result.getSize(), records);
    }

    @Override
    public KbVO getById(Long userId, Long kbId) {
        KnowledgeBase kb = getAndCheckOwner(userId, kbId);
        return toVO(kb);
    }

    @Override
    public KbVO update(Long userId, Long kbId, KbUpdateRequest request) {
        KnowledgeBase kb = getAndCheckOwner(userId, kbId);

        if (StringUtils.hasText(request.getName())) {
            kb.setName(request.getName());
        }
        if (request.getDescription() != null) {
            kb.setDescription(request.getDescription());
        }
        kbMapper.updateById(kb);
        return toVO(kb);
    }

    @Override
    @Transactional
    public void delete(Long userId, Long kbId) {
        KnowledgeBase kb = getAndCheckOwner(userId, kbId);

        List<Document> docs = documentMapper.selectList(
                new LambdaQueryWrapper<Document>()
                        .eq(Document::getKbId, kbId)
                        .eq(Document::getUserId, userId));
        for (Document doc : docs) {
            documentService.delete(userId, doc.getId());
        }

        chatMessageMapper.delete(new LambdaQueryWrapper<ChatMessage>()
                .eq(ChatMessage::getKbId, kbId)
                .eq(ChatMessage::getUserId, userId));
        chatSessionMapper.delete(new LambdaQueryWrapper<ChatSession>()
                .eq(ChatSession::getKbId, kbId)
                .eq(ChatSession::getUserId, userId));
        kbMapper.deleteById(kb.getId());
    }

    /** 获取知识库并校验所有者 */
    private KnowledgeBase getAndCheckOwner(Long userId, Long kbId) {
        KnowledgeBase kb = kbMapper.selectById(kbId);
        if (kb == null) {
            throw new BusinessException(40020, "知识库不存在");
        }
        if (!kb.getUserId().equals(userId)) {
            throw new BusinessException(40030, "无权访问该知识库");
        }
        return kb;
    }

    private KbVO toVO(KnowledgeBase kb) {
        return KbVO.builder()
                .id(kb.getId())
                .name(kb.getName())
                .description(kb.getDescription())
                .documentCount(countDocuments(kb.getUserId(), kb.getId(), null))
                .doneCount(countDocuments(kb.getUserId(), kb.getId(), "DONE"))
                .createdAt(kb.getCreatedAt())
                .updatedAt(kb.getUpdatedAt())
                .build();
    }

    private Long countDocuments(Long userId, Long kbId, String status) {
        LambdaQueryWrapper<Document> query = new LambdaQueryWrapper<Document>()
                .eq(Document::getUserId, userId)
                .eq(Document::getKbId, kbId);
        if (StringUtils.hasText(status)) {
            query.eq(Document::getStatus, status);
        }
        return documentMapper.selectCount(query);
    }
}
