package com.knowflow.service;

import com.knowflow.dto.KbCreateRequest;
import com.knowflow.dto.KbUpdateRequest;
import com.knowflow.common.PageResult;
import com.knowflow.vo.KbVO;

public interface KnowledgeBaseService {

    /** 创建知识库 */
    KbVO create(Long userId, KbCreateRequest request);

    /** 分页获取用户的知识库列表 */
    PageResult<KbVO> list(Long userId, long page, long size);

    /** 获取知识库详情 */
    KbVO getById(Long userId, Long kbId);

    /** 更新知识库 */
    KbVO update(Long userId, Long kbId, KbUpdateRequest request);

    /** 删除知识库（逻辑删除） */
    void delete(Long userId, Long kbId);
}
