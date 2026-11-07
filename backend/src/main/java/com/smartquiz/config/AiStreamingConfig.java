package com.smartquiz.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

/**
 * Bounded executors for AI work. Model calls themselves are still serialized by the AI guard;
 * these pools only cap how much work waits for or relays a model response.
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

    /**
     * Background question-generation jobs. One worker by default (the CPU-only model handles one call
     * at a time); a full queue makes POST /api/ai/generate answer 429.
     */
    @Bean(name = "aiJobExecutor", destroyMethod = "shutdown")
    public ThreadPoolTaskExecutor aiJobExecutor(@Value("${app.ai.job-threads:1}") int threads,
                                                @Value("${app.ai.job-queue:20}") int queueCapacity) {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setThreadNamePrefix("ai-job-");
        executor.setCorePoolSize(threads);
        executor.setMaxPoolSize(threads);
        executor.setQueueCapacity(queueCapacity);
        // Interrupted jobs are marked FAILED on the next startup.
        executor.setWaitForTasksToCompleteOnShutdown(false);
        executor.initialize();
        return executor;
    }
}