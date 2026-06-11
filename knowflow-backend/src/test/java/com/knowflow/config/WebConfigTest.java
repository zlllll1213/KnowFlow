package com.knowflow.config;

import org.junit.jupiter.api.Test;
import org.springframework.mock.env.MockEnvironment;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class WebConfigTest {

    @Test
    void developmentDefaultsAllowOnlyLocalFrontendOrigins() {
        WebConfig config = new WebConfig("", new MockEnvironment());

        assertThat(config.allowedOrigins())
                .containsExactlyInAnyOrder("http://localhost:5173", "http://127.0.0.1:5173");
    }

    @Test
    void productionRequiresExplicitAllowedOrigins() {
        MockEnvironment environment = new MockEnvironment();
        environment.setActiveProfiles("prod");

        assertThatThrownBy(() -> new WebConfig("", environment))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("KNOWFLOW_CORS_ALLOWED_ORIGINS");
    }

    @Test
    void wildcardOriginIsRejectedWhenCredentialsAreEnabled() {
        assertThatThrownBy(() -> new WebConfig("*", new MockEnvironment()))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("不能使用 *");
    }
}
