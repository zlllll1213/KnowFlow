package com.knowflow.controller;

import com.knowflow.dto.LoginRequest;
import com.knowflow.dto.LoginResponse;
import com.knowflow.security.AuthCookieService;
import com.knowflow.security.AuthRateLimiter;
import com.knowflow.security.ClientIpResolver;
import com.knowflow.service.AuthService;
import jakarta.servlet.http.HttpServletResponse;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AuthControllerTest {

    @Test
    void loginSetsHttpOnlyAuthCookieAndDoesNotReturnToken() {
        AuthService authService = mock(AuthService.class);
        AuthRateLimiter limiter = mock(AuthRateLimiter.class);
        ClientIpResolver ipResolver = mock(ClientIpResolver.class);
        AuthCookieService cookieService = new AuthCookieService(false, "", 86_400_000L);
        AuthController controller = new AuthController(authService, limiter, ipResolver, cookieService);
        LoginRequest loginRequest = new LoginRequest();
        loginRequest.setUsername("alice");
        loginRequest.setPassword("Passw0rd");
        MockHttpServletRequest httpRequest = new MockHttpServletRequest();
        MockHttpServletResponse httpResponse = new MockHttpServletResponse();
        when(ipResolver.resolve(httpRequest)).thenReturn("127.0.0.1");
        when(authService.login(loginRequest)).thenReturn(new LoginResponse(7L, "alice", "jwt-token"));

        LoginResponse response = controller.login(loginRequest, httpRequest, httpResponse).getData();

        assertThat(response.getToken()).isNull();
        assertThat(httpResponse.getHeader(HttpHeaders.SET_COOKIE))
                .contains("KNOWFLOW_AUTH=jwt-token")
                .contains("HttpOnly")
                .contains("SameSite=Lax");
        verify(limiter).checkLoginIpAllowed("127.0.0.1");
    }

    @Test
    void logoutClearsAuthCookie() {
        AuthController controller = new AuthController(
                mock(AuthService.class),
                mock(AuthRateLimiter.class),
                mock(ClientIpResolver.class),
                new AuthCookieService(false, "", 86_400_000L)
        );
        MockHttpServletResponse response = new MockHttpServletResponse();

        controller.logout(response);

        assertThat(response.getHeader(HttpHeaders.SET_COOKIE))
                .contains("KNOWFLOW_AUTH=")
                .contains("Max-Age=0");
    }
}
