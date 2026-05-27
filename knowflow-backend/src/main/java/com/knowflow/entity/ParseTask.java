package com.knowflow.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("parse_task")
public class ParseTask {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long documentId;

    private Long kbId;

    /** PENDING / PROCESSING / PARSING / EMBEDDING / DONE / FAILED / CANCELLED */
    private String status;

    private String errorMessage;

    private Integer retryCount;

    private LocalDateTime lastErrorAt;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedAt;
}
