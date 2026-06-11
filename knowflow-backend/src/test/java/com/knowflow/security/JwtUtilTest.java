package com.knowflow.security;

import org.junit.jupiter.api.Test;
import org.springframework.mock.env.MockEnvironment;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class JwtUtilTest {

    private static final String STRONG_SECRET = "KnowFlow-Test-Secret-For-Jwt-At-Least-32-Bytes";

    @Test
    void generatedTokenCanBeValidatedAndParsed() {
        JwtUtil jwtUtil = new JwtUtil(STRONG_SECRET, 60_000L, new MockEnvironment());

        String token = jwtUtil.generateToken(42L, "alice");

        assertThat(jwtUtil.validateToken(token)).isTrue();
        assertThat(jwtUtil.getUserIdFromToken(token)).isEqualTo(42L);
    }

    @Test
    void constructorRejectsNonPositiveExpiration() {
        assertThatThrownBy(() -> new JwtUtil(STRONG_SECRET, 0L, new MockEnvironment()))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("JWT expiration");
    }

    @Test
    void constructorRejectsDevelopmentSecretInProdProfile() {
        MockEnvironment environment = new MockEnvironment().withProperty("spring.profiles.active", "prod");
        environment.setActiveProfiles("prod");

        assertThatThrownBy(() -> new JwtUtil("KnowFlow-Dev-Secret-Only-For-Local-Development", 60_000L, environment))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("生产环境禁止使用开发 JWT secret");
    }
}
