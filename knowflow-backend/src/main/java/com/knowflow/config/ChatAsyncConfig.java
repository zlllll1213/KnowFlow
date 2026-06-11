package com.knowflow.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.util.concurrent.Executor;

@Configuration
public class ChatAsyncConfig {

    @Bean("chatSseExecutor")
    public Executor chatSseExecutor(@Value("${knowflow.sse.core-pool-size:4}") int corePoolSize,
                                    @Value("${knowflow.sse.max-pool-size:16}") int maxPoolSize,
                                    @Value("${knowflow.sse.queue-capacity:100}") int queueCapacity) {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setThreadNamePrefix("chat-sse-");
        executor.setCorePoolSize(corePoolSize);
        executor.setMaxPoolSize(maxPoolSize);
        executor.setQueueCapacity(queueCapacity);
        executor.initialize();
        return executor;
    }
}
