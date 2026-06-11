package com.knowflow.security;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class AuthCookieServiceTest {

    @Test
    void acceptsBlankAndHostnameCookieDomains() {
        assertThatCode(() -> new AuthCookieService(false, "", 60_000L)).doesNotThrowAnyException();
        assertThatCode(() -> new AuthCookieService(false, ".example.com", 60_000L)).doesNotThrowAnyException();
        assertThatCode(() -> new AuthCookieService(false, "app.example.com", 60_000L)).doesNotThrowAnyException();
    }

    @Test
    void rejectsCookieDomainsWithPortPathOrWhitespace() {
        assertThatThrownBy(() -> new AuthCookieService(false, "example.com:8080", 60_000L))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Cookie domain");
        assertThatThrownBy(() -> new AuthCookieService(false, "example.com/path", 60_000L))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Cookie domain");
        assertThatThrownBy(() -> new AuthCookieService(false, "bad domain.example", 60_000L))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Cookie domain");
    }
}
