package com.knowflow.vo;

import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@Builder
public class RecentFailedTaskVO {

    private Long taskId;
    private Long documentId;
    private String fileName;
    private String errorMessage;
    private LocalDateTime updatedAt;
}
