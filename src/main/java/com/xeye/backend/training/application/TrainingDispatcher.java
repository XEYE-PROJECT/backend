package com.xeye.backend.training.application;

import com.xeye.backend.training.application.command.TrainingLaunchCommand;
import com.xeye.backend.training.application.port.in.TrainingLaunchService;
import com.xeye.backend.training.application.port.in.TrainingLaunchUseCases;
import com.xeye.backend.training.application.port.in.TrainingUseCases;
import com.xeye.backend.training.application.port.out.TrainingLauncher;
import com.xeye.backend.training.application.port.out.TrainingMetrics;
import com.xeye.backend.training.domain.model.Training;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.Optional;
import java.util.concurrent.locks.ReentrantLock;

/**
 * La cola de entrenamientos. Lanzar = encolar ({@code queued}); este despachador arranca los
 * runs en cuanto hay hueco en los cupos (global y por usuario, con equidad: el usuario con menos
 * runs en marcha va primero). Se ejecuta tras cada encolado (en la misma petición, si el
 * candado está libre), tras cada barrido de estancados y cada pocos segundos como red de
 * seguridad. La llamada al provider ({@code TrainingLauncher.launch}) corre entre
 * transacciones, nunca dentro de una; el candado serializa los despachos porque el backend es
 * un monolito de una sola instancia.
 */
@Component
public class TrainingDispatcher implements TrainingLaunchUseCases {

    private static final Logger log = LoggerFactory.getLogger(TrainingDispatcher.class);

    private final ReentrantLock dispatchLock = new ReentrantLock();

    private final TrainingLaunchService launchService;
    private final TrainingLauncher launcher;
    private final TrainingUseCases trainings;
    private final TrainingMetrics metrics;

    public TrainingDispatcher(TrainingLaunchService launchService, TrainingLauncher launcher,
                              TrainingUseCases trainings, TrainingMetrics metrics) {
        this.launchService = launchService;
        this.launcher = launcher;
        this.trainings = trainings;
        this.metrics = metrics;
    }

    @Override
    public Training launch(Long userId, Long trainingId, String embeddingModel,
                           boolean regenerateDescriptions, boolean noDescriptions) {
        launchService.enqueue(trainingId, userId, embeddingModel, regenerateDescriptions, noDescriptions);
        dispatch();
        return trainings.get(userId, trainingId).training();
    }

    @Override
    public Training retrain(Long userId, Long listId, String embeddingModel,
                            boolean regenerateDescriptions, boolean noDescriptions) {
        Training pending;
        try {
            pending = launchService.ensurePendingForLaunch(listId, userId, embeddingModel);
        } catch (DataIntegrityViolationException ex) {
            // Una edición concurrente creó antes la fila pendiente; se reutiliza la ganadora.
            pending = launchService.ensurePendingForLaunch(listId, userId, embeddingModel);
        }
        return launch(userId, pending.id(), embeddingModel, regenerateDescriptions, noDescriptions);
    }

    /** Red de seguridad: por si un despacho se saltó (p. ej. el candado estaba ocupado al encolar). */
    @Scheduled(initialDelayString = "PT10S", fixedDelayString = "PT5S")
    public void poll() {
        try {
            dispatch();
        } catch (Exception ex) {
            log.error("Training dispatch pass failed", ex);
        }
    }

    /**
     * Arranca trainings de la cola mientras haya hueco. Si otro hilo está despachando, no espera:
     * ese hilo (o el siguiente sondeo) recogerá lo que quede.
     */
    public void dispatch() {
        if (!dispatchLock.tryLock()) {
            return;
        }
        try {
            while (true) {
                Optional<Training> next = launchService.pickNextQueued();
                if (next.isEmpty()) {
                    return;
                }
                launchOne(next.get());
            }
        } finally {
            dispatchLock.unlock();
        }
    }

    private void launchOne(Training training) {
        Long trainingId = training.id();
        TrainingLaunchCommand command;
        try {
            command = launchService.prepareLaunch(trainingId);
        } catch (Exception ex) {
            log.error("Could not prepare training {} for launch", trainingId, ex);
            launchService.markFailed(trainingId, ex.getMessage());
            launchService.ensurePending(training.listId(), training.userId());
            metrics.recordOutcome(TrainingMetrics.OUTCOME_LAUNCH_FAILED);
            return;
        }
        try {
            String instanceId = launcher.launch(command);
            launchService.markLaunched(trainingId, instanceId);
            log.info("Launched training {} for list {} (instance {})", trainingId, command.listId(), instanceId);
        } catch (Exception ex) {
            log.error("Failed to launch training {}", trainingId, ex);
            launchService.markFailed(trainingId, ex.getMessage());
            // Los datos de la lista siguen sin entrenar: se re-marca para que el usuario reintente.
            launchService.ensurePending(command.listId(), training.userId());
            metrics.recordOutcome(TrainingMetrics.OUTCOME_LAUNCH_FAILED);
        }
    }
}
