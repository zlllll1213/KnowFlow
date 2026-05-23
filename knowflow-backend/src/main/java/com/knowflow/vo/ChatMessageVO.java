package com.knowflow.vo;

import com.knowflow.dto.RagSourceChunk;
import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;

@Data
@Builder
public class ChatMessageVO {

    private Long id;
    private String role;
    private String content;
    private List<RagSourceChunk> sources;
    private LocalDateTime createdAt;
}
