package com.knowflow.dto;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class ChatSessionCreateRequest {

    @NotNull(message = "知识库 ID 不能为空")
    private Long kbId;

    private String title;
}
