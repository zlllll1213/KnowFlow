package com.knowflow.controller;

import com.knowflow.common.Result;
import com.knowflow.security.LoginUser;
import com.knowflow.service.DashboardService;
import com.knowflow.vo.DashboardStatsVO;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/dashboard")
@RequiredArgsConstructor
public class DashboardController {

    private final DashboardService dashboardService;

    @GetMapping("/stats")
    public Result<DashboardStatsVO> stats(@AuthenticationPrincipal LoginUser loginUser) {
        if (loginUser == null) {
            return Result.error(40100, "未登录");
        }
        return Result.success(dashboardService.getStats(loginUser.getUserId()));
    }
}
