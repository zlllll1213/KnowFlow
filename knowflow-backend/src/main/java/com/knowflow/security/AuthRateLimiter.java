package com.knowflow.security;

import com.knowflow.common.BusinessException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Component;

import java.time.Duration;

@Slf4j
@Component
public class AuthRateLimiter {

    private static final String KEY_PREFIX = "login_attempts:";
    private static final String LOGIN_IP_PREFIX = "login_attempts:ip:";
    private static final String REGISTER_IP_PREFIX = "register_attempts:ip:";

    private final RedisTemplate<String, Object> redisTemplate;
    private final int maxAttempts;
    private final long ttlSeconds;

    public AuthRateLimiter(RedisTemplate<String, Object> redisTemplate,
                           @Value("${knowflow.auth.max-login-attempts:5}") int maxAttempts,
                           @Value("${knowflow.auth.login-lock-seconds:300}") long ttlSeconds) {
        this.redisTemplate = redisTemplate;
        this.maxAttempts = maxAttempts;
        this.ttlSeconds = ttlSeconds;
    }

    public void checkAllowed(String username) {
        Object value = redisTemplate.opsForValue().get(key(username));
        if (parseAttempts(value) >= maxAttempts) {
            throw new BusinessException(42900, "登录失败次数过多，请稍后再试");
        }
    }

    public void checkLoginIpAllowed(String clientIp) {
        checkIpAllowed(LOGIN_IP_PREFIX, clientIp, "登录请求过于频繁，请稍后再试");
    }

    public void checkRegisterIpAllowed(String clientIp) {
        checkIpAllowed(REGISTER_IP_PREFIX, clientIp, "注册请求过于频繁，请稍后再试");
    }

    public void recordFailure(String username) {
        String key = key(username);
        Long attempts = redisTemplate.opsForValue().increment(key);
        if (attempts != null && attempts == 1L) {
            redisTemplate.expire(key, Duration.ofSeconds(ttlSeconds));
        }
    }

    public void recordSuccess(String username) {
        redisTemplate.delete(key(username));
    }

    public void recordLoginIpFailure(String clientIp) {
        recordIpFailure(LOGIN_IP_PREFIX, clientIp);
    }

    public void recordRegisterIpFailure(String clientIp) {
        recordIpFailure(REGISTER_IP_PREFIX, clientIp);
    }

    public void recordLoginIpSuccess(String clientIp) {
        redisTemplate.delete(ipKey(LOGIN_IP_PREFIX, clientIp));
    }

    public void recordRegisterIpSuccess(String clientIp) {
        redisTemplate.delete(ipKey(REGISTER_IP_PREFIX, clientIp));
    }

    private String key(String username) {
        String normalized = username == null ? "" : username.trim().toLowerCase();
        return KEY_PREFIX + normalized;
    }

    private void checkIpAllowed(String prefix, String clientIp, String message) {
        Object value = redisTemplate.opsForValue().get(ipKey(prefix, clientIp));
        if (parseAttempts(value) >= maxAttempts) {
            throw new BusinessException(42900, message);
        }
    }

    private void recordIpFailure(String prefix, String clientIp) {
        String key = ipKey(prefix, clientIp);
        Long attempts = redisTemplate.opsForValue().increment(key);
        if (attempts != null && attempts == 1L) {
            redisTemplate.expire(key, Duration.ofSeconds(ttlSeconds));
        }
    }

    private String ipKey(String prefix, String clientIp) {
        String normalized = clientIp == null || clientIp.isBlank() ? "unknown" : clientIp.trim();
        return prefix + normalized;
    }

    private long parseAttempts(Object value) {
        if (value instanceof Number number) {
            return number.longValue();
        }
        if (value instanceof String text) {
            try {
                return Long.parseLong(text);
            } catch (NumberFormatException e) {
                log.debug("忽略无法解析的登录失败计数: {}", text);
            }
        }
        return 0;
    }
}
