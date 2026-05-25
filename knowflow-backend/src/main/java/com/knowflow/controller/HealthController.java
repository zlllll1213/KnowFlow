package com.knowflow.controller;

import com.knowflow.common.Result;
import com.knowflow.util.RagClient;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.RedisCallback;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.LinkedHashMap;
import java.util.Map;

@RestController
@RequestMapping("/api/health")
@RequiredArgsConstructor
public class HealthController {

    private final JdbcTemplate jdbcTemplate;
    private final StringRedisTemplate redisTemplate;
    private final RagClient ragClient;

    @Value("${storage.type:local}")
    private String storageType;

    @Value("${storage.local-path:./storage}")
    private String storageLocalPath;

    @Value("${knowflow.rag.base-url:http://localhost:8090}")
    private String ragBaseUrl;

    @GetMapping
    public Result<Map<String, Object>> health() {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("status", "ok");
        data.put("database", checkDatabase());
        data.put("redis", checkRedis());
        data.put("rag", checkRag());
        data.put("storage", Map.of(
                "type", storageType,
                "localPath", storageLocalPath
        ));
        data.put("ragBaseUrl", ragBaseUrl);
        return Result.success(data);
    }

    private Map<String, Object> checkDatabase() {
        try {
            Integer value = jdbcTemplate.queryForObject("SELECT 1", Integer.class);
            return status(value != null && value == 1, null);
        } catch (Exception e) {
            return status(false, e.getMessage());
        }
    }

    private Map<String, Object> checkRedis() {
        try {
            String pong = redisTemplate.execute((RedisCallback<String>) connection -> connection.ping());
            return status("PONG".equalsIgnoreCase(pong), null);
        } catch (Exception e) {
            return status(false, e.getMessage());
        }
    }

    private Map<String, Object> checkRag() {
        boolean available = ragClient.isAvailable();
        return status(available, available ? null : "Go RAG Service 不可用");
    }

    private Map<String, Object> status(boolean ok, String error) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("ok", ok);
        if (error != null && !error.isBlank()) {
            result.put("error", error);
        }
        return result;
    }
}
