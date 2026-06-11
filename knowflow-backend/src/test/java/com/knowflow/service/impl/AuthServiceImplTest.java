package com.knowflow.service.impl;

import com.knowflow.common.BusinessException;
import com.knowflow.dto.LoginRequest;
import com.knowflow.dto.LoginResponse;
import com.knowflow.dto.RegisterRequest;
import com.knowflow.entity.User;
import com.knowflow.mapper.UserMapper;
import com.knowflow.security.AuthRateLimiter;
import com.knowflow.security.JwtUtil;
import com.knowflow.vo.UserVO;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AuthServiceImplTest {

    @Mock
    private UserMapper userMapper;

    @Mock
    private PasswordEncoder passwordEncoder;

    @Mock
    private JwtUtil jwtUtil;

    @Mock
    private AuthRateLimiter authRateLimiter;

    @InjectMocks
    private AuthServiceImpl authService;

    @Test
    void registerRejectsUsernameThatIsBlankAfterTrim() {
        RegisterRequest request = new RegisterRequest();
        request.setUsername("   ");
        request.setPassword("Passw0rd");
        request.setEmail("alice@example.com");

        assertThatThrownBy(() -> authService.register(request))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("用户名不能为空");

        verify(userMapper, never()).selectCount(any());
        verify(userMapper, never()).insert(any());
    }

    @Test
    void registerRejectsWeakPasswordWhenServiceIsCalledDirectly() {
        RegisterRequest request = new RegisterRequest();
        request.setUsername("alice");
        request.setPassword("password");
        request.setEmail("alice@example.com");

        assertThatThrownBy(() -> authService.register(request))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("密码必须同时包含字母和数字");

        verify(userMapper, never()).selectCount(any());
        verify(passwordEncoder, never()).encode(any());
        verify(userMapper, never()).insert(any());
    }

    @Test
    void registerNormalizesInputAndStoresEncodedPassword() {
        RegisterRequest request = new RegisterRequest();
        request.setUsername("  alice  ");
        request.setPassword("Passw0rd");
        request.setEmail("  Alice@Example.COM  ");
        when(userMapper.selectCount(any())).thenReturn(0L);
        when(passwordEncoder.encode("Passw0rd")).thenReturn("encoded-password");

        UserVO response = authService.register(request);

        ArgumentCaptor<User> userCaptor = ArgumentCaptor.forClass(User.class);
        verify(userMapper).insert(userCaptor.capture());
        User storedUser = userCaptor.getValue();
        assertThat(storedUser.getUsername()).isEqualTo("alice");
        assertThat(storedUser.getEmail()).isEqualTo("alice@example.com");
        assertThat(storedUser.getPasswordHash()).isEqualTo("encoded-password");
        assertThat(response.getUsername()).isEqualTo("alice");
    }

    @Test
    void loginRecordsFailureWhenUserDoesNotExist() {
        LoginRequest request = new LoginRequest();
        request.setUsername(" alice ");
        request.setPassword("Passw0rd");
        when(userMapper.selectOne(any())).thenReturn(null);

        assertThatThrownBy(() -> authService.login(request))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("用户名或密码错误");

        verify(authRateLimiter).checkAllowed("alice");
        verify(authRateLimiter).recordFailure("alice");
        verify(authRateLimiter, never()).recordSuccess(any());
    }

    @Test
    void loginReturnsTokenAndClearsLimiterAfterPasswordMatches() {
        LoginRequest request = new LoginRequest();
        request.setUsername(" alice ");
        request.setPassword("Passw0rd");
        User user = new User();
        user.setId(7L);
        user.setUsername("alice");
        user.setPasswordHash("encoded-password");
        when(userMapper.selectOne(any())).thenReturn(user);
        when(passwordEncoder.matches("Passw0rd", "encoded-password")).thenReturn(true);
        when(jwtUtil.generateToken(7L, "alice")).thenReturn("jwt-token");

        LoginResponse response = authService.login(request);

        assertThat(response.getUserId()).isEqualTo(7L);
        assertThat(response.getUsername()).isEqualTo("alice");
        assertThat(response.getToken()).isEqualTo("jwt-token");
        verify(authRateLimiter).checkAllowed("alice");
        verify(authRateLimiter).recordSuccess("alice");
        verify(authRateLimiter, never()).recordFailure(any());
    }
}
