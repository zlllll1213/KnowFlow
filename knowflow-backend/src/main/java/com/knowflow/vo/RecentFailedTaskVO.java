package com.knowflow.vo;

import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;

import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RecentFailedTaskVO {

    private Long taskId;
    private Long documentId;
    private String fileName;
    private String errorMessage;
    private LocalDateTime updatedAt;
}
