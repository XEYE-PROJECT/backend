package com.xeye.backend.shared.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.task.TaskExecutor;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

/**
 * Executor de fondo del provider mock de training (simula la finalización fuera del hilo de la
 * petición). El scheduling alimenta el despachador de la cola de trainings, el barrido de
 * estancados, el relé del outbox y las retenciones. Los eventos hacia el buscador ya no van por
 * {@code @Async}: se persisten en el outbox y los entrega su relé.
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
}
