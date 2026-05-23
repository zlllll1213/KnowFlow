package com.knowflow.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * Go RAG Service 问答返回体。
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class RagResponse {

    private String answer;
    private List<RagSourceChunk> sources;
    private Long latencyMs;
}
