package com.knowflow.service;

import com.knowflow.common.PageResult;
import com.knowflow.vo.DocumentVO;
import org.springframework.web.multipart.MultipartFile;

public interface DocumentService {

    /** 上传文档 */
    DocumentVO upload(Long userId, Long kbId, MultipartFile file);

    /** 分页获取知识库下的文档列表 */
    PageResult<DocumentVO> listByKb(Long userId, Long kbId, long page, long size);

    /** 获取文档详情 */
    DocumentVO getById(Long userId, Long docId);

    /** 删除文档 */
    void delete(Long userId, Long docId);

    /** 获取文档解析状态 */
    String getStatus(Long userId, Long docId);
}
