package com.knowflow.entity;

import com.baomidou.mybatisplus.annotation.*;
import com.knowflow.util.PgVectorTypeHandler;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName(value = "document_chunk", autoResultMap = true)
public class DocumentChunk {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long documentId;

    private Long kbId;

    private Integer chunkIndex;

    private String content;

    /** 文本向量，底层以 pgvector VECTOR(1536) 存储。 */
    @TableField(typeHandler = PgVectorTypeHandler.class)
    private float[] embedding;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;
}
