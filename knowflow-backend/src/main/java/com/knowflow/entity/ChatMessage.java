package com.knowflow.entity;

import com.baomidou.mybatisplus.annotation.*;
import com.knowflow.util.JsonbTypeHandler;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName(value = "chat_message", autoResultMap = true)
public class ChatMessage {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long sessionId;

    private Long kbId;

    private Long userId;

    /** user / assistant */
    private String role;

    private String content;

    /** 引用来源 SourceChunk JSON 数组 */
    @TableField(typeHandler = JsonbTypeHandler.class)
    private String sources;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;
}
