package com.knowflow.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class ChatAskRequest {

    @NotNull(message = "知识库 ID 不能为空")
    private Long kbId;

    @NotNull(message = "会话 ID 不能为空")
    private Long sessionId;

    @NotBlank(message = "问题不能为空")
    private String question;
}
