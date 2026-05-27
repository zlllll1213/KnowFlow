package com.knowflow.controller;

import com.knowflow.common.PageResult;
import com.knowflow.common.Result;
import com.knowflow.dto.PageRequest;
import com.knowflow.security.LoginUser;
import com.knowflow.service.DocumentService;
import com.knowflow.vo.DocumentVO;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/document")
@RequiredArgsConstructor
public class DocumentController {

    private final DocumentService documentService;

    /** 上传文档 */
    @PostMapping("/upload")
    public Result<DocumentVO> upload(@AuthenticationPrincipal LoginUser loginUser,
                                     @RequestParam Long kbId,
                                     @RequestParam MultipartFile file) {
        DocumentVO doc = documentService.upload(loginUser.getUserId(), kbId, file);
        return Result.success("上传成功", doc);
    }

    /** 文档列表 */
    @GetMapping("/list")
    public Result<PageResult<DocumentVO>> list(@AuthenticationPrincipal LoginUser loginUser,
                                               @RequestParam Long kbId,
                                               @RequestParam(required = false) Long page,
                                               @RequestParam(required = false) Long size) {
        PageResult<DocumentVO> list = documentService.listByKb(loginUser.getUserId(), kbId,
                PageRequest.normalizePage(page), PageRequest.normalizeSize(size));
        return Result.success(list);
    }

    /** 文档详情 */
    @GetMapping("/{id}")
    public Result<DocumentVO> getById(@AuthenticationPrincipal LoginUser loginUser,
                                      @PathVariable Long id) {
        DocumentVO doc = documentService.getById(loginUser.getUserId(), id);
        return Result.success(doc);
    }

    /** 删除文档 */
    @DeleteMapping("/{id}")
    public Result<Void> delete(@AuthenticationPrincipal LoginUser loginUser,
                               @PathVariable Long id) {
        documentService.delete(loginUser.getUserId(), id);
        return Result.success("删除成功", null);
    }

    /** 文档解析状态 */
    @GetMapping("/{id}/status")
    public Result<String> status(@AuthenticationPrincipal LoginUser loginUser,
                                 @PathVariable Long id) {
        String status = documentService.getStatus(loginUser.getUserId(), id);
        return Result.success(status);
    }
}
