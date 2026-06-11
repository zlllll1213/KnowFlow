package com.knowflow.util;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.knowflow.common.BusinessException;
import com.knowflow.dto.RagResponse;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestTemplate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.http.HttpMethod.POST;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

class RagClientTest {

    @Test
    void askUsesMockFallbackWhenRagServiceReturnsEmptyBody() {
        RagClient client = new RagClient("http://rag-service", 100, 100, true, new ObjectMapper());
        RestTemplate restTemplate = (RestTemplate) ReflectionTestUtils.getField(client, "restTemplate");
        MockRestServiceServer server = MockRestServiceServer.bindTo(restTemplate).build();
        server.expect(requestTo("http://rag-service/rag/ask"))
                .andExpect(method(POST))
                .andRespond(withSuccess("", MediaType.APPLICATION_JSON));

        RagResponse response = client.ask(11L, "介绍一下知识库", 5);

        assertThat(response.getAnswer()).contains("模拟回答");
        assertThat(response.getSources()).isEmpty();
        assertThat(response.getLatencyMs()).isZero();
        server.verify();
    }

    @Test
    void askFailsWhenRagServiceReturnsEmptyBodyAndFallbackIsDisabled() {
        RagClient client = new RagClient("http://rag-service", 100, 100, false, new ObjectMapper());
        RestTemplate restTemplate = (RestTemplate) ReflectionTestUtils.getField(client, "restTemplate");
        MockRestServiceServer server = MockRestServiceServer.bindTo(restTemplate).build();
        server.expect(requestTo("http://rag-service/rag/ask"))
                .andExpect(method(POST))
                .andRespond(withSuccess("", MediaType.APPLICATION_JSON));

        assertThatThrownBy(() -> client.ask(11L, "介绍一下知识库", 5))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("fallback disabled");
        server.verify();
    }

    @Test
    void askUsesMockFallbackWhenRagServiceReturnsServerError() {
        RagClient client = new RagClient("http://rag-service", 100, 100, true, new ObjectMapper());
        RestTemplate restTemplate = (RestTemplate) ReflectionTestUtils.getField(client, "restTemplate");
        MockRestServiceServer server = MockRestServiceServer.bindTo(restTemplate).build();
        server.expect(requestTo("http://rag-service/rag/ask"))
                .andExpect(method(POST))
                .andRespond(withStatus(HttpStatus.SERVICE_UNAVAILABLE));

        RagResponse response = client.ask(12L, "服务异常时怎么办", 5);

        assertThat(response.getAnswer()).contains("模拟回答");
        assertThat(response.getSources()).isEmpty();
        server.verify();
    }
}
