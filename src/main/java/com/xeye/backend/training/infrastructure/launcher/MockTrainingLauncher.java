package com.xeye.backend.training.infrastructure.launcher;

import com.xeye.backend.training.application.command.TrainingLaunchCommand;
import com.xeye.backend.training.application.command.TrainingUpdateCommand;
import com.xeye.backend.training.application.port.in.TrainingCompletionHandler;
import com.xeye.backend.training.application.port.out.TrainingLauncher;
import com.xeye.backend.training.config.TrainingProperties;
import com.xeye.backend.training.domain.model.TrainingCost;
import com.xeye.backend.training.domain.model.TrainingTime;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.core.task.TaskExecutor;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.Map;

/**
 * Provider por defecto (dev): sin servicios externos. Tras un pequeño retardo llama al handler
 * de finalización con embeddings de relleno, ejercitando de punta a punta el camino completo
 * (elementos marcados como entrenados, in_use fijado, push a búsqueda).
 */
@Component
@ConditionalOnProperty(name = "xeye.training.provider", havingValue = "mock", matchIfMissing = true)
public class MockTrainingLauncher implements TrainingLauncher {

    private static final Logger log = LoggerFactory.getLogger(MockTrainingLauncher.class);

    private final TrainingCompletionHandler completionHandler;
    private final TaskExecutor executor;
    private final long delayMs;

    public MockTrainingLauncher(TrainingCompletionHandler completionHandler,
                                @Qualifier("trainingTaskExecutor") TaskExecutor executor,
                                TrainingProperties properties) {
        this.completionHandler = completionHandler;
        this.executor = executor;
        this.delayMs = Math.max(100, properties.mockDelayMs());
    }

    @Override
    public String launch(TrainingLaunchCommand command) {
        log.info("[mock] running training {} for list {} ({} elements)",
                command.trainingId(), command.listId(), command.elements().size());
        executor.execute(() -> simulateCompletion(command));
        return "mock-" + command.trainingId();
    }

    private void simulateCompletion(TrainingLaunchCommand command) {
        try {
            Thread.sleep(delayMs);
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            return;
        }
        String model = "{\"embedding_model\":\"mock\",\"llm_model\":null,\"list_id\":" + command.listId() + "}";
        String embeddings = Base64.getEncoder()
                .encodeToString(("mock-embeddings-list-" + command.listId()).getBytes(StandardCharsets.UTF_8));
        TrainingUpdateCommand update = new TrainingUpdateCommand(
                command.trainingId(), "completed", embeddings, model,
                new TrainingTime(0L, 0L, 0L), new TrainingCost(0.0, 0.0), null, Map.of());
        try {
            completionHandler.applyUpdate(update);
            log.info("[mock] completed training {}", command.trainingId());
        } catch (Exception ex) {
            log.error("[mock] failed to complete training {}", command.trainingId(), ex);
        }
    }
}
