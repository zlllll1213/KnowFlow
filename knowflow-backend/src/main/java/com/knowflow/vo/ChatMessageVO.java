package com.knowflow.vo;

import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@Builder
public class ChatMessageVO {

    private Long id;
    private String role;
    private String content;
    private LocalDateTime createdAt;
}
