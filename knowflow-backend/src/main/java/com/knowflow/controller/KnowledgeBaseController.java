package com.knowflow.controller;

import com.knowflow.common.Result;
import com.knowflow.common.PageResult;
import com.knowflow.dto.KbCreateRequest;
import com.knowflow.dto.KbUpdateRequest;
import com.knowflow.dto.PageRequest;
import com.knowflow.security.LoginUser;
import com.knowflow.service.KnowledgeBaseService;
import com.knowflow.vo.KbVO;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/kb")
@RequiredArgsConstructor
public class KnowledgeBaseController {

    private final KnowledgeBaseService kbService;

    /** 创建知识库 */
    @PostMapping
    public Result<KbVO> create(@AuthenticationPrincipal LoginUser loginUser,
                               @Valid @RequestBody KbCreateRequest request) {
        KbVO kb = kbService.create(loginUser.getUserId(), request);
        return Result.success("创建成功", kb);
    }

    /** 获取知识库列表 */
    @GetMapping("/list")
    public Result<PageResult<KbVO>> list(@AuthenticationPrincipal LoginUser loginUser,
                                         @RequestParam(required = false) Long page,
                                         @RequestParam(required = false) Long size) {
        PageResult<KbVO> list = kbService.list(loginUser.getUserId(),
                PageRequest.normalizePage(page), PageRequest.normalizeSize(size));
        return Result.success(list);
    }

    /** 获取知识库详情 */
    @GetMapping("/{id}")
    public Result<KbVO> getById(@AuthenticationPrincipal LoginUser loginUser,
                                @PathVariable Long id) {
        KbVO kb = kbService.getById(loginUser.getUserId(), id);
        return Result.success(kb);
    }

    /** 更新知识库 */
    @PutMapping("/{id}")
    public Result<KbVO> update(@AuthenticationPrincipal LoginUser loginUser,
                               @PathVariable Long id,
                               @RequestBody KbUpdateRequest request) {
        KbVO kb = kbService.update(loginUser.getUserId(), id, request);
        return Result.success("更新成功", kb);
    }

    /** 删除知识库 */
    @DeleteMapping("/{id}")
    public Result<Void> delete(@AuthenticationPrincipal LoginUser loginUser,
                               @PathVariable Long id) {
        kbService.delete(loginUser.getUserId(), id);
        return Result.success("删除成功", null);
    }
}
