package com.knowflow.service.impl;

import com.knowflow.entity.ParseTask;
import com.knowflow.mapper.ParseTaskMapper;
import com.knowflow.service.TaskService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
public class TaskServiceImpl implements TaskService {

    private final ParseTaskMapper parseTaskMapper;
    private final StringRedisTemplate redisTemplate;

    /** Redis 队列 Key */
    private static final String PARSE_QUEUE_KEY = "knowflow:parse:queue";

    @Override
    public void createParseTask(Long documentId, Long kbId) {
        // 创建 parse_task 记录
        ParseTask task = new ParseTask();
        task.setDocumentId(documentId);
        task.setKbId(kbId);
        task.setStatus("PENDING");
        parseTaskMapper.insert(task);

        // 将 taskId 推入 Redis 队列
        redisTemplate.opsForList().rightPush(PARSE_QUEUE_KEY, task.getId().toString());

        log.info("解析任务已创建: taskId={}, documentId={}, 已推入队列 {}", task.getId(), documentId, PARSE_QUEUE_KEY);
    }
}
