package com.knowflow.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * Go RAG Service 返回的引用来源片段。
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class RagSourceChunk {

    private Long chunkId;
    private Long documentId;
    private String fileName;
    private Integer chunkIndex;
    private String content;
    private Double score;
}
