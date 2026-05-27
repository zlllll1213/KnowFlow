package com.knowflow.service.impl;

import com.knowflow.entity.ParseTask;
import com.knowflow.mapper.ParseTaskMapper;
import com.knowflow.service.TaskService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

@Slf4j
@Service
@RequiredArgsConstructor
public class TaskServiceImpl implements TaskService {

    private final ParseTaskMapper parseTaskMapper;
    private final StringRedisTemplate redisTemplate;

    /** Redis 队列 Key */
    private static final String PARSE_QUEUE_KEY = "knowflow:parse:queue";

    @Override
    public Long createParseTask(Long documentId, Long kbId) {
        ParseTask task = new ParseTask();
        task.setDocumentId(documentId);
        task.setKbId(kbId);
        task.setStatus("PENDING");
        parseTaskMapper.insert(task);

        Runnable enqueue = () -> enqueueParseTask(task.getId(), documentId);
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    enqueue.run();
                }
            });
            log.info("解析任务已创建，等待事务提交后入队: taskId={}, documentId={}", task.getId(), documentId);
        } else {
            enqueue.run();
        }

        return task.getId();
    }

    private void enqueueParseTask(Long taskId, Long documentId) {
        redisTemplate.opsForList().rightPush(PARSE_QUEUE_KEY, taskId.toString());
        log.info("解析任务已入队: taskId={}, documentId={}, queue={}", taskId, documentId, PARSE_QUEUE_KEY);
    }
}
