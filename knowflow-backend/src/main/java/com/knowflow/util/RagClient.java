package com.knowflow.util;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.knowflow.common.BusinessException;
import com.knowflow.dto.AgentResponse;
import com.knowflow.dto.RagResponse;
import com.knowflow.dto.RagSourceChunk;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Go RAG Service HTTP 客户端。
 */
@Slf4j
@Component
public class RagClient {

    private final RestTemplate restTemplate;
    private final String baseUrl;
    private final ObjectMapper objectMapper;
    private final boolean mockFallbackEnabled;

    public RagClient(
            @Value("${knowflow.rag.base-url:http://localhost:8090}") String baseUrl,
            @Value("${knowflow.rag.connect-timeout-ms:3000}") int connectTimeoutMs,
            @Value("${knowflow.rag.read-timeout-ms:120000}") int readTimeoutMs,
            @Value("${knowflow.rag.mock-fallback-enabled:false}") boolean mockFallbackEnabled,
            ObjectMapper objectMapper) {
        this.baseUrl = baseUrl;
        this.objectMapper = objectMapper;
        this.mockFallbackEnabled = mockFallbackEnabled;
        SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
        requestFactory.setConnectTimeout(connectTimeoutMs);
        requestFactory.setReadTimeout(readTimeoutMs);
        this.restTemplate = new RestTemplate(requestFactory);
    }

    /**
     * 调用 Go RAG Service 同步问答。
     */
    public RagResponse ask(Long kbId, String question, int topK) {
        Map<String, Object> body = Map.of(
                "kbId", kbId,
                "question", question,
                "topK", topK
        );
        return postRag("/rag/ask", body, RagResponse.class, kbId, question);
    }

    public AgentResponse askAgent(Long kbId, String question, int topK) {
        Map<String, Object> body = Map.of(
                "kbId", kbId,
                "question", question,
                "topK", topK
        );
        return postRag("/agent/ask", body, AgentResponse.class, kbId, question);
    }

    private <T> T postRag(String path, Map<String, Object> body, Class<T> responseType, Long kbId, String question) {
        try {
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            HttpEntity<Map<String, Object>> request = new HttpEntity<>(body, headers);

            T response = restTemplate.postForObject(baseUrl + path, request, responseType);

            if (response != null) {
                if (response instanceof RagResponse ragResponse) {
                    if (ragResponse.getSources() == null) {
                        ragResponse.setSources(Collections.emptyList());
                    }
                    log.info("RAG 问答完成: kbId={}, sources={}, latencyMs={}",
                            kbId, ragResponse.getSources().size(), ragResponse.getLatencyMs());
                } else if (response instanceof AgentResponse agentResponse) {
                    if (agentResponse.getSources() == null) {
                        agentResponse.setSources(Collections.emptyList());
                    }
                    log.info("Agent 问答完成: kbId={}, intent={}, sources={}, confidence={}",
                            kbId, agentResponse.getIntent(), agentResponse.getSources().size(), agentResponse.getConfidence());
                }
                return response;
            }
        } catch (RestClientException e) {
            log.warn("Go RAG Service 调用失败: path={}, kbId={}, error={}", path, kbId, e.getMessage());
            if (mockFallbackEnabled && responseType == RagResponse.class) {
                return responseType.cast(mockResponse(kbId, question));
            }
        }

        throw new BusinessException(50301, "RAG 服务暂不可用，请稍后重试");
    }

    public void askStream(Long kbId, String question, int topK, StreamListener listener) {
        stream("/rag/ask/stream", kbId, question, topK, listener);
    }

    public void askAgentStream(Long kbId, String question, int topK, StreamListener listener) {
        stream("/agent/ask/stream", kbId, question, topK, listener);
    }

    private void stream(String path, Long kbId, String question, int topK, StreamListener listener) {
        try {
            Map<String, Object> body = Map.of(
                    "kbId", kbId,
                    "question", question,
                    "topK", topK
            );

            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            HttpEntity<Map<String, Object>> request = new HttpEntity<>(body, headers);

            restTemplate.execute(baseUrl + path, org.springframework.http.HttpMethod.POST,
                    clientHttpRequest -> {
                        clientHttpRequest.getHeaders().putAll(request.getHeaders());
                        objectMapper.writeValue(clientHttpRequest.getBody(), body);
                    },
                    clientHttpResponse -> {
                        try (BufferedReader reader = new BufferedReader(new InputStreamReader(
                                clientHttpResponse.getBody(), StandardCharsets.UTF_8))) {
                            String line;
                            while ((line = reader.readLine()) != null) {
                                if (!line.startsWith("data:")) {
                                    continue;
                                }
                                handleStreamEvent(line.substring(5).trim(), listener);
                            }
                        }
                        return null;
                    });
        } catch (Exception e) {
            log.warn("Go RAG Service 流式接口调用失败: path={}, kbId={}, error={}", path, kbId, e.getMessage());
            if (mockFallbackEnabled && path.startsWith("/rag/")) {
                RagResponse fallback = mockResponse(kbId, question);
                listener.onToken(fallback.getAnswer());
                listener.onSources(Collections.emptyList());
                listener.onDone();
                return;
            }
            listener.onError("RAG 服务暂不可用，请稍后重试");
        }
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

    @SuppressWarnings("unchecked")
    private void handleStreamEvent(String json, StreamListener listener) throws JsonProcessingException {
        if (json == null || json.isBlank()) {
            return;
        }
        Map<String, Object> event = objectMapper.readValue(json, Map.class);
        String type = Objects.toString(event.get("type"), "");
        switch (type) {
            case "token" -> listener.onToken(Objects.toString(event.get("content"), ""));
            case "answer" -> listener.onToken(Objects.toString(event.get("content"), Objects.toString(event.get("answer"), "")));
            case "sources" -> {
                Object rawSources = event.get("sources");
                List<RagSourceChunk> sources = rawSources == null
                        ? Collections.emptyList()
                        : objectMapper.convertValue(rawSources,
                                objectMapper.getTypeFactory().constructCollectionType(List.class, RagSourceChunk.class));
                listener.onSources(sources);
            }
            case "error" -> listener.onError(Objects.toString(event.get("message"), "RAG 流式问答失败"));
            case "meta" -> listener.onMeta(event);
            case "done" -> listener.onDone();
            default -> {
            }
        }
    }

    public interface StreamListener {
        void onToken(String token);

        void onSources(List<RagSourceChunk> sources);

        void onError(String message);

        default void onMeta(Map<String, Object> meta) {
        }

        void onDone();
    }

    // ---------- 显式开发 mock ----------

    private RagResponse mockResponse(Long kbId, String question) {
        RagResponse resp = new RagResponse();
        resp.setAnswer("这是基于知识库 [ID=" + kbId + "] 的模拟回答。您的问题是：「" + question + "」。"
                + "\n\n提示：当前启用了开发 mock fallback。"
                + "请启动 Go RAG Service 以获取真实回答。");
        resp.setSources(Collections.emptyList());
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
