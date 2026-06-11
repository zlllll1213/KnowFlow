package com.knowflow.security;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseCookie;
import org.springframework.stereotype.Component;

import java.time.Duration;

@Component
public class AuthCookieService {

    public static final String AUTH_COOKIE_NAME = "KNOWFLOW_AUTH";

    private final boolean secure;
    private final String domain;
    private final long expirationMs;

    public AuthCookieService(@Value("${knowflow.cookie.secure:false}") boolean secure,
                             @Value("${knowflow.cookie.domain:}") String domain,
                             @Value("${jwt.expiration}") long expirationMs) {
        this.secure = secure;
        this.domain = domain == null ? "" : domain.trim();
        this.expirationMs = expirationMs;
    }

    public void addAuthCookie(jakarta.servlet.http.HttpServletResponse response, String token) {
        response.addHeader(HttpHeaders.SET_COOKIE, baseCookie(token)
                .maxAge(Duration.ofMillis(expirationMs))
                .build()
                .toString());
    }

    public void clearAuthCookie(jakarta.servlet.http.HttpServletResponse response) {
        response.addHeader(HttpHeaders.SET_COOKIE, baseCookie("")
                .maxAge(Duration.ZERO)
                .build()
                .toString());
    }

    private ResponseCookie.ResponseCookieBuilder baseCookie(String value) {
        ResponseCookie.ResponseCookieBuilder builder = ResponseCookie.from(AUTH_COOKIE_NAME, value)
                .httpOnly(true)
                .secure(secure)
                .path("/")
                .sameSite("Lax");
        if (!domain.isBlank()) {
            builder.domain(domain);
        }
        return builder;
    }
}
