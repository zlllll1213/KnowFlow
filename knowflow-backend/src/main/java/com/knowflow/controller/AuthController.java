package com.knowflow.controller;

import com.knowflow.common.Result;
import com.knowflow.dto.LoginRequest;
import com.knowflow.dto.LoginResponse;
import com.knowflow.dto.RegisterRequest;
import com.knowflow.security.LoginUser;
import com.knowflow.service.AuthService;
import com.knowflow.vo.UserVO;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthService authService;

    /** 注册 */
    @PostMapping("/register")
    public Result<UserVO> register(@Valid @RequestBody RegisterRequest request) {
        UserVO user = authService.register(request);
        return Result.success("注册成功", user);
    }

    /** 登录 */
    @PostMapping("/login")
    public Result<LoginResponse> login(@Valid @RequestBody LoginRequest request) {
        LoginResponse resp = authService.login(request);
        return Result.success("登录成功", resp);
    }

    /** 获取当前用户信息 */
    @GetMapping("/me")
    public Result<UserVO> me(@AuthenticationPrincipal LoginUser loginUser) {
        UserVO user = authService.getCurrentUser(loginUser.getUserId());
        return Result.success(user);
    }
}
