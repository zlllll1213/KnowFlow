package com.knowflow.util;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.knowflow.dto.RagResponse;
import com.knowflow.dto.RagSourceChunk;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;

import java.util.Collections;
import java.util.List;
import java.util.Map;

/**
 * Go RAG Service HTTP 客户端。
 * 当 Go 服务不可用时，自动降级为 mock 回答。
 */
@Slf4j
@Component
public class RagClient {

    private final RestTemplate restTemplate;
    private final String baseUrl;
    private final ObjectMapper objectMapper;

    public RagClient(
            @Value("${knowflow.rag.base-url:http://localhost:8090}") String baseUrl,
            ObjectMapper objectMapper) {
        this.baseUrl = baseUrl;
        this.objectMapper = objectMapper;
        this.restTemplate = new RestTemplate();
    }

    /**
     * 调用 Go RAG Service 同步问答。
     * 失败时降级为 mock 回答。
     */
    public RagResponse ask(Long kbId, String question, int topK) {
        try {
            Map<String, Object> body = Map.of(
                    "kbId", kbId,
                    "question", question,
                    "topK", topK
            );

            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            HttpEntity<Map<String, Object>> request = new HttpEntity<>(body, headers);

            RagResponse response = restTemplate.postForObject(
                    baseUrl + "/rag/ask", request, RagResponse.class);

            if (response != null) {
                log.info("RAG 问答完成: kbId={}, sources={}, latencyMs={}",
                        kbId, response.getSources().size(), response.getLatencyMs());
                return response;
            }
        } catch (RestClientException e) {
            log.warn("Go RAG Service 不可用 ({}), 降级为 mock", e.getMessage());
        }

        return mockResponse(kbId, question);
    }

    /**
     * Health check: 检查 Go RAG Service 是否可用。
     */
    public boolean isAvailable() {
        try {
            String result = restTemplate.getForObject(baseUrl + "/health", String.class);
            return result != null && result.contains("ok");
        } catch (Exception e) {
            return false;
        }
    }

    // ---------- mock 降级 ----------

    private RagResponse mockResponse(Long kbId, String question) {
        RagSourceChunk mockSource = new RagSourceChunk(
                0L, 0L, "mock-notice.txt", 0,
                "[MOCK] 当前为模拟数据。请确保 Go RAG Service 已启动，"
                        + "且知识库中已有已解析的文档切片。",
                0.0);

        RagResponse resp = new RagResponse();
        resp.setAnswer("这是基于知识库 [ID=" + kbId + "] 的模拟回答。您的问题是：「" + question + "」。"
                + "\n\n⚠️ 提示：当前 Go RAG Service 不可用，返回了 mock 数据。"
                + "请启动 Go RAG Service 以获取真实回答。");
        resp.setSources(Collections.singletonList(mockSource));
        resp.setLatencyMs(0L);
        return resp;
    }

    // ---------- 兼容旧代码 ----------

    /** @deprecated 使用 ask() 替代 */
    @Deprecated
    public String mockAsk(String question, Long kbId) {
        return ask(kbId, question, 5).getAnswer();
    }
}
