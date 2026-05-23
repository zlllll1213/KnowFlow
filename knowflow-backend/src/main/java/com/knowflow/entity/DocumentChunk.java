package com.knowflow.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("document_chunk")
public class DocumentChunk {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long documentId;

    private Long kbId;

    private Integer chunkIndex;

    private String content;

    /** 文本向量，pgvector VECTOR(1536) 类型 */
    private String embedding;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;
}
