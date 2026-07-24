package com.xeye.backend.training.infrastructure.web;

import com.xeye.backend.shared.security.AuthenticatedUser;
import com.xeye.backend.training.application.port.in.TrainingLaunchUseCases;
import com.xeye.backend.training.application.port.in.TrainingUseCases;
import com.xeye.backend.training.config.TrainingProperties;
import com.xeye.backend.training.infrastructure.web.dto.EmbeddingModelsResponse;
import com.xeye.backend.training.infrastructure.web.dto.LaunchTrainingRequest;
import com.xeye.backend.training.infrastructure.web.dto.TrainingResponse;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/** Endpoints de training: lecturas de historial/detalle, pendientes y el lanzamiento manual. */
@RestController
public class TrainingController {

    private final TrainingUseCases trainings;
    private final TrainingLaunchUseCases launches;
    private final TrainingProperties properties;

    public TrainingController(TrainingUseCases trainings, TrainingLaunchUseCases launches,
                              TrainingProperties properties) {
        this.trainings = trainings;
        this.launches = launches;
        this.properties = properties;
    }

    @GetMapping("/lists/{listId}/trainings")
    public List<TrainingResponse> listByList(@AuthenticationPrincipal AuthenticatedUser current,
                                             @PathVariable Long listId) {
        return trainings.listByList(current.id(), listId).stream().map(TrainingResponse::from).toList();
    }

    /** Trainings pendientes del usuario en todas sus listas — los avisos de reentrenar. */
    @GetMapping("/trainings/pending")
    public List<TrainingResponse> pending(@AuthenticationPrincipal AuthenticatedUser current) {
        return trainings.pendingForUser(current.id()).stream().map(TrainingResponse::from).toList();
    }

    /** Modelos de embedding con los que se puede lanzar un training. */
    @GetMapping("/trainings/embedding-models")
    public EmbeddingModelsResponse embeddingModels() {
        return EmbeddingModelsResponse.of(properties.embeddingModels());
    }

    @GetMapping("/trainings/{id}")
    public TrainingResponse get(@AuthenticationPrincipal AuthenticatedUser current, @PathVariable Long id) {
        return TrainingResponse.from(trainings.get(current.id(), id));
    }

    /**
     * Reentrena la lista ya mismo con el modelo elegido (body opcional): reutiliza el training
     * pendiente si una edición lo marcó, o crea uno al vuelo — el usuario siempre puede
     * relanzar, aunque nada haya cambiado.
     */
    @PostMapping("/lists/{listId}/trainings")
    public TrainingResponse retrain(@AuthenticationPrincipal AuthenticatedUser current,
                                    @PathVariable Long listId,
                                    @RequestBody(required = false) LaunchTrainingRequest request) {
        String embeddingModel = request == null ? null : request.embeddingModel();
        return TrainingResponse.from(launches.retrain(current.id(), listId, embeddingModel));
    }

    /** Lanza un training pendiente con el modelo de embedding elegido (body opcional). */
    @PostMapping("/trainings/{id}/launch")
    public TrainingResponse launch(@AuthenticationPrincipal AuthenticatedUser current,
                                   @PathVariable Long id,
                                   @RequestBody(required = false) LaunchTrainingRequest request) {
        String embeddingModel = request == null ? null : request.embeddingModel();
        return TrainingResponse.from(launches.launch(current.id(), id, embeddingModel));
    }
}
