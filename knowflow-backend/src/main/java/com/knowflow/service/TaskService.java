package com.knowflow.service;

public interface TaskService {

    /** 为文档创建解析任务；如果存在 Spring 事务，任务 ID 会在事务提交后推入 Redis 队列。 */
    Long createParseTask(Long documentId, Long kbId);
}
