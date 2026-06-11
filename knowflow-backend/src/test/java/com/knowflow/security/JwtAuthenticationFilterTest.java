package com.knowflow.security;

import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.env.MockEnvironment;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.core.context.SecurityContextHolder;

import static org.assertj.core.api.Assertions.assertThat;

class JwtAuthenticationFilterTest {

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void authenticatesJwtFromHttpOnlyCookie() throws Exception {
        JwtUtil jwtUtil = new JwtUtil(
                "KnowFlow-Test-Secret-For-Jwt-At-Least-32-Bytes",
                60_000L,
                new MockEnvironment()
        );
        String token = jwtUtil.generateToken(42L, "alice");
        JwtAuthenticationFilter filter = new JwtAuthenticationFilter(jwtUtil);
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setCookies(new Cookie(AuthCookieService.AUTH_COOKIE_NAME, token));

        filter.doFilter(request, new MockHttpServletResponse(), new MockFilterChain());

        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNotNull();
        LoginUser loginUser = (LoginUser) SecurityContextHolder.getContext().getAuthentication().getPrincipal();
        assertThat(loginUser.getUserId()).isEqualTo(42L);
    }
}
