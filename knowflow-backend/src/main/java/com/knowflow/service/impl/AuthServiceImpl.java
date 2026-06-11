package com.knowflow.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.knowflow.common.BusinessException;
import com.knowflow.dto.LoginRequest;
import com.knowflow.dto.LoginResponse;
import com.knowflow.dto.RegisterRequest;
import com.knowflow.entity.User;
import com.knowflow.mapper.UserMapper;
import com.knowflow.security.AuthRateLimiter;
import com.knowflow.security.JwtUtil;
import com.knowflow.service.AuthService;
import com.knowflow.vo.UserVO;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.util.Locale;

@Service
@RequiredArgsConstructor
public class AuthServiceImpl implements AuthService {

    private static final int MIN_PASSWORD_LENGTH = 8;
    private static final int MAX_PASSWORD_LENGTH = 128;

    private final UserMapper userMapper;
    private final PasswordEncoder passwordEncoder;
    private final JwtUtil jwtUtil;
    private final AuthRateLimiter authRateLimiter;

    @Override
    public UserVO register(RegisterRequest request) {
        String username = request.getUsername().trim();
        String email = request.getEmail().trim().toLowerCase(Locale.ROOT);

        // 服务层同样做 fail-fast，避免绕过 Controller 校验时写入非法账号。
        if (username.isBlank()) {
            throw new BusinessException(40014, "用户名不能为空");
        }
        validatePassword(request.getPassword());

        // 检查用户名是否已存在
        Long count = userMapper.selectCount(
                new LambdaQueryWrapper<User>().eq(User::getUsername, username));
        if (count > 0) {
            throw new BusinessException(40010, "用户名已存在");
        }

        // 检查邮箱是否已存在
        count = userMapper.selectCount(
                new LambdaQueryWrapper<User>().eq(User::getEmail, email));
        if (count > 0) {
            throw new BusinessException(40011, "邮箱已被注册");
        }

        User user = new User();
        user.setUsername(username);
        user.setPasswordHash(passwordEncoder.encode(request.getPassword()));
        user.setEmail(email);
        userMapper.insert(user);

        return UserVO.builder()
                .id(user.getId())
                .username(user.getUsername())
                .email(user.getEmail())
                .createdAt(user.getCreatedAt())
                .build();
    }

    @Override
    public LoginResponse login(LoginRequest request) {
        String username = request.getUsername().trim();
        authRateLimiter.checkAllowed(username);

        User user = userMapper.selectOne(
                new LambdaQueryWrapper<User>().eq(User::getUsername, username));

        if (user == null) {
            authRateLimiter.recordFailure(username);
            throw new BusinessException(40012, "用户名或密码错误");
        }

        if (!passwordEncoder.matches(request.getPassword(), user.getPasswordHash())) {
            authRateLimiter.recordFailure(username);
            throw new BusinessException(40012, "用户名或密码错误");
        }

        String token = jwtUtil.generateToken(user.getId(), user.getUsername());
        authRateLimiter.recordSuccess(username);

        return new LoginResponse(user.getId(), user.getUsername(), token);
    }

    @Override
    public UserVO getCurrentUser(Long userId) {
        User user = userMapper.selectById(userId);
        if (user == null) {
            throw new BusinessException(40013, "用户不存在");
        }
        return UserVO.builder()
                .id(user.getId())
                .username(user.getUsername())
                .email(user.getEmail())
                .createdAt(user.getCreatedAt())
                .build();
    }

    private void validatePassword(String password) {
        if (password == null || password.length() < MIN_PASSWORD_LENGTH || password.length() > MAX_PASSWORD_LENGTH) {
            throw new BusinessException(40015, "密码长度 8-128");
        }
        // 服务层复用强度兜底，防止内部调用绕过 DTO 校验。
        if (!password.matches("^(?=.*[A-Za-z])(?=.*\\d).+$")) {
            throw new BusinessException(40016, "密码必须同时包含字母和数字");
        }
    }
}
