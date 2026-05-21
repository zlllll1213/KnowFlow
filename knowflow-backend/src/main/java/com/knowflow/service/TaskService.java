package com.knowflow.service;

public interface TaskService {

    /** 为文档创建解析任务，并将任务 ID 推入 Redis 队列 */
    void createParseTask(Long documentId, Long kbId);
}
