package com.xeye.backend.training.application;

import com.xeye.backend.training.application.command.TrainingLaunchCommand;
import com.xeye.backend.training.application.port.in.TrainingLaunchService;
import com.xeye.backend.training.application.port.in.TrainingLaunchUseCases;
import com.xeye.backend.training.application.port.in.TrainingUseCases;
import com.xeye.backend.training.application.port.out.TrainingLauncher;
import com.xeye.backend.training.domain.model.Training;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Component;

/**
 * Dirige el lanzamiento, a petición del usuario, de un training pendiente. Está fuera de
 * {@link TrainingLaunchService} para que la llamada al provider ({@code TrainingLauncher.launch})
 * corra entre transacciones, nunca dentro de una.
 */
@Component
public class TrainingLaunchOrchestrator implements TrainingLaunchUseCases {

    private static final Logger log = LoggerFactory.getLogger(TrainingLaunchOrchestrator.class);

    private final TrainingLaunchService launchService;
    private final TrainingLauncher launcher;
    private final TrainingUseCases trainings;

    public TrainingLaunchOrchestrator(TrainingLaunchService launchService, TrainingLauncher launcher,
                                      TrainingUseCases trainings) {
        this.launchService = launchService;
        this.launcher = launcher;
        this.trainings = trainings;
    }

    @Override
    public Training launch(Long userId, Long trainingId, String embeddingModel) {
        TrainingLaunchCommand command = launchService.prepareLaunch(trainingId, userId, embeddingModel);
        try {
            String instanceId = launcher.launch(command);
            launchService.markLaunched(trainingId, instanceId);
            log.info("Launched training {} for list {} (instance {})",
                    trainingId, command.listId(), instanceId);
        } catch (Exception ex) {
            log.error("Failed to launch training {}", trainingId, ex);
            launchService.markFailed(trainingId, ex.getMessage());
            // Los datos de la lista siguen sin entrenar: se re-marca para que el usuario reintente.
            launchService.ensurePending(command.listId(), userId);
        }
        return trainings.get(userId, trainingId);
    }

    @Override
    public Training retrain(Long userId, Long listId, String embeddingModel) {
        Training pending;
        try {
            pending = launchService.ensurePendingForLaunch(listId, userId, embeddingModel);
        } catch (DataIntegrityViolationException ex) {
            // Una edición concurrente creó antes la fila pendiente; se reutiliza la ganadora.
            pending = launchService.ensurePendingForLaunch(listId, userId, embeddingModel);
        }
        return launch(userId, pending.id(), embeddingModel);
    }
}
