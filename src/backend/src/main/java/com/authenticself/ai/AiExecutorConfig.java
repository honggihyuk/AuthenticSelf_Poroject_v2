package com.authenticself.ai;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.util.concurrent.Executor;

/**
 * Dedicated thread pool for parallel AI downstream calls (Task-4 FR-11 / AC-13).
 * <p>
 * The pool isolates AI I/O from the Tomcat request threadpool so a slow
 * Python call cannot starve other HTTP handlers. Core size = 2 is enough
 * to run space + style in parallel at idle; max = 8 leaves headroom for
 * the {@code AnalysisPoller}'s batch sweep (FR-14) to fan out a few rows
 * concurrently. Queue = 64 prevents excessive task rejection under bursts.
 * <p>
 * Thread-name prefix {@code ai-orchestrator-} makes pool contention
 * immediately visible in thread dumps.
 */
@Configuration
public class AiExecutorConfig {

    @Bean(name = "aiExecutor")
    public Executor aiExecutor(
            @Value("${app.ai.orchestrator.executor.core-size:${app.ai.executor.core-size:2}}")
            int coreSize,
            @Value("${app.ai.orchestrator.executor.max-size:${app.ai.executor.max-size:8}}")
            int maxSize,
            @Value("${app.ai.orchestrator.executor.queue-capacity:${app.ai.executor.queue-capacity:64}}")
            int queueCapacity
    ) {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(coreSize);
        executor.setMaxPoolSize(maxSize);
        executor.setQueueCapacity(queueCapacity);
        executor.setThreadNamePrefix("ai-orchestrator-");
        // Allow workers above core to die back after idle so we don't hold
        // 8 threads forever when the system is quiet.
        executor.setAllowCoreThreadTimeOut(false);
        executor.setKeepAliveSeconds(60);
        executor.initialize();
        return executor;
    }
}
