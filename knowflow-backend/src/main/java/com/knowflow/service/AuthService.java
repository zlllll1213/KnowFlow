package com.knowflow.service;

import com.knowflow.dto.LoginRequest;
import com.knowflow.dto.LoginResponse;
import com.knowflow.dto.RegisterRequest;
import com.knowflow.vo.UserVO;

public interface AuthService {

    /** 用户注册 */
    UserVO register(RegisterRequest request);

    /** 用户登录，返回 JWT token */
    LoginResponse login(LoginRequest request);

    /** 获取当前登录用户信息 */
    UserVO getCurrentUser(Long userId);
}
