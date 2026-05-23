package com.knowflow.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("document")
public class Document {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long kbId;

    private Long userId;

    private String fileName;

    private String filePath;

    private Long fileSize;

    private String fileType;

    /** UPLOADED / PARSING / EMBEDDING / DONE / FAILED */
    private String status;

    /** 已完成的切片数量 */
    private Integer chunkCount;

    private String errorMessage;

    @TableLogic
    private Integer isDeleted;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedAt;
}
