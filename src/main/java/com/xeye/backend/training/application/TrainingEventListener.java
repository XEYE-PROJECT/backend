package com.xeye.backend.training.application;

import com.xeye.backend.shared.event.TrainingRequestedEvent;
import com.xeye.backend.training.application.port.in.TrainingLaunchService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * Conecta las ediciones de listas/elementos con el flujo de training. Una edición ya no lanza
 * nada: marca la lista con un training PENDING (máximo uno por lista) y el usuario decide
 * cuándo lanzarlo — y con qué modelo — desde la pantalla de trainings. Corre en asíncrono
 * tras el commit para no bloquear nunca la respuesta HTTP.
 */
@Component
public class TrainingEventListener {

    private static final Logger log = LoggerFactory.getLogger(TrainingEventListener.class);

    private final TrainingLaunchService launchService;

    public TrainingEventListener(TrainingLaunchService launchService) {
        this.launchService = launchService;
    }

    @Async("trainingTaskExecutor")
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onTrainingRequested(TrainingRequestedEvent event) {
        try {
            launchService.ensurePending(event.listId(), event.userId());
            log.debug("Training request for list {} — {}", event.listId(), event.reason());
        } catch (Exception ex) {
            log.error("Failed to flag a pending training for list {}", event.listId(), ex);
        }
    }
}
