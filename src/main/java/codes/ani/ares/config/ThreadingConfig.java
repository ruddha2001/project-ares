package codes.ani.ares.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.task.AsyncTaskExecutor;
import org.springframework.core.task.support.TaskExecutorAdapter;

import java.util.concurrent.Executors;

/**
 * Configuration class for thread pool and async task execution setup.
 * Provides Spring bean definitions for async task execution using virtual threads.
 */
@Configuration
public class ThreadingConfig {
    /**
     * Creates and configures an AsyncTaskExecutor bean using virtual threads.
     * Virtual threads provide lightweight, scalable concurrency for async operations.
     *
     * @return an AsyncTaskExecutor configured to use virtual threads per task
     */
    @Bean
    public AsyncTaskExecutor aresTaskExecutor() {
        return new TaskExecutorAdapter(Executors.newVirtualThreadPerTaskExecutor());
    }
}
