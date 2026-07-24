package com.xeye.backend.shared.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.task.TaskExecutor;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

/**
 * Executor de fondo del flujo de training: el lanzamiento (llamada HTTP saliente) y la
 * finalización simulada del provider mock corren fuera del hilo de la petición. El scheduling
 * alimenta el barrido de trainings estancados ({@code TrainingStalledSweeper}).
 */
@Configuration
@EnableAsync
@EnableScheduling
public class AsyncConfig {

    @Bean(name = "trainingTaskExecutor")
    public TaskExecutor trainingTaskExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(2);
        executor.setMaxPoolSize(4);
        executor.setQueueCapacity(50);
        executor.setThreadNamePrefix("training-");
        executor.initialize();
        return executor;
    }

    /** Notificaciones fire-and-forget de cambios al microservicio de búsqueda (ver módulo search). */
    @Bean(name = "searchSyncTaskExecutor")
    public TaskExecutor searchSyncTaskExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(1);
        executor.setMaxPoolSize(2);
        executor.setQueueCapacity(200);
        executor.setThreadNamePrefix("search-sync-");
        executor.initialize();
        return executor;
    }
}
