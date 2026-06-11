package com.knowflow.security;

import com.knowflow.common.BusinessException;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AuthRateLimiterTest {

    @Test
    void checkAllowedRejectsLoginWhenAttemptsReachLimit() {
        RedisTemplate<String, Object> redisTemplate = mockRedisTemplate();
        when(redisTemplate.opsForValue().get("login_attempts:alice")).thenReturn("5");
        AuthRateLimiter limiter = new AuthRateLimiter(redisTemplate, 5, 300);

        assertThatThrownBy(() -> limiter.checkAllowed("alice"))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("登录失败次数过多");
    }

    @Test
    void recordFailureSetsTtlAndSuccessClearsKey() {
        RedisTemplate<String, Object> redisTemplate = mockRedisTemplate();
        ValueOperations<String, Object> ops = redisTemplate.opsForValue();
        when(ops.increment("login_attempts:bob")).thenReturn(1L);
        AuthRateLimiter limiter = new AuthRateLimiter(redisTemplate, 5, 300);

        assertThatCode(() -> limiter.recordFailure("bob")).doesNotThrowAnyException();
        limiter.recordSuccess("bob");

        verify(redisTemplate).expire(eq("login_attempts:bob"), eq(Duration.ofSeconds(300)));
        verify(redisTemplate).delete("login_attempts:bob");
    }

    @Test
    void registerIpLimiterUsesSeparateRedisKey() {
        RedisTemplate<String, Object> redisTemplate = mockRedisTemplate();
        ValueOperations<String, Object> ops = redisTemplate.opsForValue();
        when(ops.increment("register_attempts:ip:127.0.0.1")).thenReturn(1L);
        AuthRateLimiter limiter = new AuthRateLimiter(redisTemplate, 5, 300);

        limiter.recordRegisterIpFailure("127.0.0.1");
        limiter.recordRegisterIpSuccess("127.0.0.1");

        verify(redisTemplate).expire(eq("register_attempts:ip:127.0.0.1"), eq(Duration.ofSeconds(300)));
        verify(redisTemplate).delete("register_attempts:ip:127.0.0.1");
    }

    @SuppressWarnings("unchecked")
    private RedisTemplate<String, Object> mockRedisTemplate() {
        RedisTemplate<String, Object> redisTemplate = mock(RedisTemplate.class);
        ValueOperations<String, Object> ops = mock(ValueOperations.class);
        when(redisTemplate.opsForValue()).thenReturn(ops);
        return redisTemplate;
    }
}
