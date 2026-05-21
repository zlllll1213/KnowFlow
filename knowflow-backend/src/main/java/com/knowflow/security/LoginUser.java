package com.knowflow.security;

import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * 当前登录用户上下文。由 JwtAuthenticationFilter 设置，
 * 后续 Controller 通过 SecurityContextHolder 获取。
 */
@Getter
@AllArgsConstructor
public class LoginUser {

    private final Long userId;
}
