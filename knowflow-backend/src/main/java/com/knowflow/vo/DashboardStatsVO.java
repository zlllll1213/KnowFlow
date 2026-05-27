package com.knowflow.vo;

import lombok.Builder;
import lombok.Data;

import java.util.List;

@Data
@Builder
public class DashboardStatsVO {

    private long knowledgeBaseCount;
    private long documentCount;
    private long parsedDocumentCount;
    private long chunkCount;
    private long sessionCount;
    private long questionCount;
    private List<DocumentVO> recentDocuments;
    private List<String> recentFailedTasks;
}
