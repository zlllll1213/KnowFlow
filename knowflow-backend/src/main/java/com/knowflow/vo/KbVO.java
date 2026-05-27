package com.knowflow.vo;

import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@Builder
public class KbVO {

    private Long id;
    private String name;
    private String description;
    private Long documentCount;
    private Long doneCount;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
