package com.knowflow.controller;

import com.knowflow.common.Result;
import com.knowflow.dto.LoginRequest;
import com.knowflow.dto.LoginResponse;
import com.knowflow.dto.RegisterRequest;
import com.knowflow.common.BusinessException;
import com.knowflow.security.AuthCookieService;
import com.knowflow.security.AuthRateLimiter;
import com.knowflow.security.ClientIpResolver;
import com.knowflow.security.LoginUser;
import com.knowflow.service.AuthService;
import com.knowflow.vo.UserVO;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthService authService;
    private final AuthRateLimiter authRateLimiter;
    private final ClientIpResolver clientIpResolver;
    private final AuthCookieService authCookieService;

    /** 注册 */
    @PostMapping("/register")
    public Result<UserVO> register(@Valid @RequestBody RegisterRequest request,
                                   HttpServletRequest httpRequest) {
        String clientIp = clientIp(httpRequest);
        authRateLimiter.checkRegisterIpAllowed(clientIp);
        try {
            UserVO user = authService.register(request);
            authRateLimiter.recordRegisterIpSuccess(clientIp);
            return Result.success("注册成功", user);
        } catch (BusinessException e) {
            // 注册失败同样计数，避免同一来源无限枚举用户名和邮箱。
            authRateLimiter.recordRegisterIpFailure(clientIp);
            throw e;
        }
    }

    /** 登录 */
    @PostMapping("/login")
    public Result<LoginResponse> login(@Valid @RequestBody LoginRequest request,
                                       HttpServletRequest httpRequest,
                                       HttpServletResponse httpResponse) {
        String clientIp = clientIp(httpRequest);
        authRateLimiter.checkLoginIpAllowed(clientIp);
        try {
            LoginResponse resp = authService.login(request);
            authCookieService.addAuthCookie(httpResponse, resp.getToken());
            authRateLimiter.recordLoginIpSuccess(clientIp);
            return Result.success("登录成功", new LoginResponse(resp.getUserId(), resp.getUsername(), null));
        } catch (BusinessException e) {
            if (e.getCode() == 40012) {
                authRateLimiter.recordLoginIpFailure(clientIp);
            }
            throw e;
        }
    }

    /** 获取当前用户信息 */
    @GetMapping("/me")
    public Result<UserVO> me(@AuthenticationPrincipal LoginUser loginUser) {
        UserVO user = authService.getCurrentUser(loginUser.getUserId());
        return Result.success(user);
    }

    @GetMapping("/csrf")
    public Result<Map<String, String>> csrf(CsrfToken csrfToken) {
        return Result.success(Map.of("token", csrfToken.getToken()));
    }

    @PostMapping("/logout")
    public Result<Void> logout(HttpServletResponse response) {
        authCookieService.clearAuthCookie(response);
        return Result.success("退出成功", null);
    }

    private String clientIp(HttpServletRequest request) {
        return clientIpResolver.resolve(request);
    }
}
