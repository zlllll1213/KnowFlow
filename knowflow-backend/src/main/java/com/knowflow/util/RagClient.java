package com.knowflow.util;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * RAG 服务客户端 — 预留后续调用 Go RAG 服务。
 * 当前版本返回模拟回答。
 */
@Slf4j
@Component
public class RagClient {

    /**
     * 模拟问答——后续替换为 HTTP 调用 Go RAG 服务。
     *
     * @param question 用户问题
     * @param kbId     知识库 ID
     * @return 模拟回答
     */
    public String mockAsk(String question, Long kbId) {
        log.info("RagClient.mockAsk: kbId={}, question={}", kbId, question);
        return "这是基于知识库 [ID=" + kbId + "] 的模拟回答。您的问题是：「" + question + "」。后续将接入 Go RAG 服务获得真实回答。";
    }
}
