package com.knowflow.service.security;

import com.knowflow.common.BusinessException;
import com.knowflow.entity.KnowledgeBase;
import com.knowflow.mapper.KnowledgeBaseMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class OwnershipChecker {

    private final KnowledgeBaseMapper kbMapper;

    public KnowledgeBase requireKbOwner(Long userId, Long kbId) {
        KnowledgeBase kb = kbMapper.selectById(kbId);
        if (kb == null) {
            throw new BusinessException(40020, "知识库不存在");
        }
        if (!kb.getUserId().equals(userId)) {
            throw new BusinessException(40030, "无权访问该知识库");
        }
        return kb;
    }
}
