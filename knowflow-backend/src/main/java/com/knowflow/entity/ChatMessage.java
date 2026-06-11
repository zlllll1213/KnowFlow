package com.knowflow.entity;

import com.baomidou.mybatisplus.annotation.*;
import com.knowflow.dto.RagSourceChunk;
import com.knowflow.util.RagSourceChunkListTypeHandler;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;

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

    /** 引用来源片段，底层以 JSONB 存储。 */
    @TableField(typeHandler = RagSourceChunkListTypeHandler.class)
    private List<RagSourceChunk> sources;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;
}
