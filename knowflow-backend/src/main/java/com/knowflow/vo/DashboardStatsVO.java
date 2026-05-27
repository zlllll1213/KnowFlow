package com.knowflow.vo;

import com.knowflow.entity.ChatSession;
import lombok.Builder;
import lombok.Data;

import java.util.List;

@Data
@Builder
public class DashboardStatsVO {

    private long kbCount;
    private long docCount;
    private long doneDocCount;
    private long failedDocCount;
    private long chunkCount;
    private long chatCount;
    private List<DocumentVO> recentDocs;
    private List<ChatSession> recentSessions;
    private List<RecentFailedTaskVO> recentFailedTasks;
}
