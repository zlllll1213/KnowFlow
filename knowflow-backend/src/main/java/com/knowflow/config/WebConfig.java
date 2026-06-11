package com.knowflow.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import java.util.Arrays;
import java.util.List;

@Configuration
public class WebConfig implements WebMvcConfigurer {

    private static final List<String> DEV_ALLOWED_ORIGINS = List.of(
            "http://localhost:5173",
            "http://127.0.0.1:5173"
    );

    private final List<String> allowedOrigins;

    public WebConfig(@Value("${knowflow.cors.allowed-origins:}") String configuredOrigins,
                     Environment environment) {
        boolean prod = Arrays.asList(environment.getActiveProfiles()).contains("prod");
        List<String> parsed = parseOrigins(configuredOrigins);
        if (parsed.contains("*")) {
            throw new IllegalStateException("启用 credentials 时 CORS 不能使用 *");
        }
        if (prod && parsed.isEmpty()) {
            throw new IllegalStateException("生产环境必须配置 KNOWFLOW_CORS_ALLOWED_ORIGINS");
        }
        this.allowedOrigins = parsed.isEmpty() ? DEV_ALLOWED_ORIGINS : parsed;
    }

    @Override
    public void addCorsMappings(CorsRegistry registry) {
        registry.addMapping("/api/**")
                .allowedOrigins(allowedOrigins.toArray(String[]::new))
                .allowedMethods("GET", "POST", "PUT", "DELETE", "OPTIONS")
                .allowedHeaders("*")
                .allowCredentials(true);
    }

    List<String> allowedOrigins() {
        return allowedOrigins;
    }

    private List<String> parseOrigins(String configuredOrigins) {
        if (configuredOrigins == null || configuredOrigins.isBlank()) {
            return List.of();
        }
        return Arrays.stream(configuredOrigins.split(","))
                .map(String::trim)
                .filter(value -> !value.isBlank())
                .toList();
    }
}
