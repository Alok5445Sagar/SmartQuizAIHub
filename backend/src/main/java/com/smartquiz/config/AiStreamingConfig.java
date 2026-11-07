package com.smartquiz.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

/**
 * Bounded pool for SSE chat streams. Model calls themselves are still serialized by the AI guard;
 * this pool only caps how many open streams wait for or relay a model response.
 */
@Configuration
public class AiStreamingConfig {

    @Bean(name = "chatStreamExecutor", destroyMethod = "shutdown")
    public ThreadPoolTaskExecutor chatStreamExecutor(@Value("${app.ai.stream-threads:8}") int maxThreads,
                                                     @Value("${app.ai.stream-queue:20}") int queueCapacity) {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setThreadNamePrefix("chat-stream-");
        executor.setCorePoolSize(Math.min(2, maxThreads));
        executor.setMaxPoolSize(maxThreads);
        executor.setQueueCapacity(queueCapacity);
        executor.setWaitForTasksToCompleteOnShutdown(false);
        executor.initialize();
        return executor;
    }
}
