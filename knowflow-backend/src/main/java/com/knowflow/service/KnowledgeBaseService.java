package com.knowflow.service;

import com.knowflow.dto.KbCreateRequest;
import com.knowflow.dto.KbUpdateRequest;
import com.knowflow.vo.KbVO;

import java.util.List;

public interface KnowledgeBaseService {

    /** 创建知识库 */
    KbVO create(Long userId, KbCreateRequest request);

    /** 获取用户的知识库列表 */
    List<KbVO> list(Long userId);

    /** 获取知识库详情 */
    KbVO getById(Long userId, Long kbId);

    /** 更新知识库 */
    KbVO update(Long userId, Long kbId, KbUpdateRequest request);

    /** 删除知识库（逻辑删除） */
    void delete(Long userId, Long kbId);
}
