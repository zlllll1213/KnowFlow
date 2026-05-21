package com.knowflow.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class KbCreateRequest {

    @NotBlank(message = "知识库名称不能为空")
    private String name;

    private String description;
}
