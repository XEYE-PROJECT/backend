package com.xeye.backend.training.application;

import com.xeye.backend.training.application.port.in.TrainingLaunchService;
import com.xeye.backend.training.config.TrainingProperties;
import com.xeye.backend.training.domain.model.Training;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.Instant;
import java.util.List;

/**
 * Red de seguridad para workers muertos: un training lanzado solo avanza vía webhooks, así que
 * si el contenedor muere (o el callback nunca llega) la fila quedaría corriendo para siempre.
 * Este barrido falla los runs sin actualizar en {@code xeye.training.stalled-after-minutes}
 * (0 lo desactiva) y re-marca la lista como PENDING para que el usuario simplemente reentrene;
 * si el worker resucita y llama después, las transiciones laxas aún aplican su estado final.
 */
@Component
public class TrainingStalledSweeper {

    private static final Logger log = LoggerFactory.getLogger(TrainingStalledSweeper.class);

    private final TrainingLaunchService launchService;
    private final TrainingProperties properties;

    public TrainingStalledSweeper(TrainingLaunchService launchService, TrainingProperties properties) {
        this.launchService = launchService;
        this.properties = properties;
    }

    @Scheduled(initialDelayString = "PT1M", fixedDelayString = "PT2M")
    public void sweep() {
        long minutes = properties.stalledAfterMinutes();
        if (minutes <= 0) {
            return;
        }
        Instant cutoff = Instant.now().minus(Duration.ofMinutes(minutes));
        List<Training> stalled = launchService.failStalled(cutoff);
        for (Training training : stalled) {
            // El lanzamiento des-entrenó los elementos de la lista, así que de verdad necesita reentrenar.
            launchService.ensurePending(training.listId(), training.userId());
        }
        if (!stalled.isEmpty()) {
            log.info("Marked {} stalled training(s) as failed", stalled.size());
        }
    }
}
