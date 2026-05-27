package com.knowflow.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class AgentResponse {

    private String intent;
    private String answer;
    private List<RagSourceChunk> sources;
    private Double confidence;
    private List<AgentTraceStep> trace;
    private Long latencyMs;
}
