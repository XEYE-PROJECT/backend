package com.xeye.backend.training.infrastructure.web;

import com.xeye.backend.shared.paging.PageResponse;
import com.xeye.backend.shared.paging.Paging;
import com.xeye.backend.shared.security.AuthenticatedUser;
import com.xeye.backend.training.application.port.in.TrainingLaunchUseCases;
import com.xeye.backend.training.application.port.in.TrainingUseCases;
import com.xeye.backend.training.config.TrainingProperties;
import com.xeye.backend.training.infrastructure.web.dto.EmbeddingModelsResponse;
import com.xeye.backend.training.infrastructure.web.dto.LaunchTrainingRequest;
import com.xeye.backend.training.infrastructure.web.dto.TrainingCostEstimateResponse;
import com.xeye.backend.training.infrastructure.web.dto.TrainingResponse;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/** Endpoints de training: lecturas de historial/detalle, pendientes y el lanzamiento manual (encolado). */
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

    /** Historial de la lista paginado ({@code ?offset&limit}, máx. 200), los más recientes primero. */
    @GetMapping("/lists/{listId}/trainings")
    public PageResponse<TrainingResponse> listByList(@AuthenticationPrincipal AuthenticatedUser current,
                                                     @PathVariable Long listId,
                                                     @RequestParam(required = false) Integer offset,
                                                     @RequestParam(required = false) Integer limit) {
        return PageResponse.from(trainings.listByList(current.id(), listId, Paging.of(offset, limit)),
                TrainingResponse::from);
    }

    /**
     * Precio preestablecido de lanzar un entrenamiento de la lista ahora mismo.
     * {@code ?regenerateDescriptions=true} = como si se regenerase el enriquecimiento LLM de
     * todos los elementos, ignorando el cacheado. {@code ?noDescriptions=true} = sin
     * descripciones IA (gana a regenerar).
     */
    @GetMapping("/lists/{listId}/trainings/estimate")
    public TrainingCostEstimateResponse estimate(@AuthenticationPrincipal AuthenticatedUser current,
                                                 @PathVariable Long listId,
                                                 @RequestParam(defaultValue = "false") boolean regenerateDescriptions,
                                                 @RequestParam(defaultValue = "false") boolean noDescriptions) {
        return TrainingCostEstimateResponse.from(
                trainings.estimateCost(current.id(), listId, regenerateDescriptions, noDescriptions));
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
     * relanzar, aunque nada haya cambiado. El run entra en cola y arranca cuando hay hueco.
     */
    @PostMapping("/lists/{listId}/trainings")
    public TrainingResponse retrain(@AuthenticationPrincipal AuthenticatedUser current,
                                    @PathVariable Long listId,
                                    @RequestBody(required = false) LaunchTrainingRequest request) {
        String embeddingModel = request == null ? null : request.embeddingModel();
        boolean regenerate = request != null && request.regenerate();
        boolean noDescriptions = request != null && request.skipDescriptions();
        launches.retrain(current.id(), listId, embeddingModel, regenerate, noDescriptions);
        return latestOf(current, listId);
    }

    /** Activa este training como el modelo en uso de la lista (debe cubrir sus elementos actuales). */
    @PostMapping("/trainings/{id}/use")
    public TrainingResponse use(@AuthenticationPrincipal AuthenticatedUser current, @PathVariable Long id) {
        trainings.use(current.id(), id);
        return TrainingResponse.from(trainings.get(current.id(), id));
    }

    /** Encola un training pendiente con el modelo de embedding elegido (body opcional). */
    @PostMapping("/trainings/{id}/launch")
    public TrainingResponse launch(@AuthenticationPrincipal AuthenticatedUser current,
                                   @PathVariable Long id,
                                   @RequestBody(required = false) LaunchTrainingRequest request) {
        String embeddingModel = request == null ? null : request.embeddingModel();
        boolean regenerate = request != null && request.regenerate();
        boolean noDescriptions = request != null && request.skipDescriptions();
        launches.launch(current.id(), id, embeddingModel, regenerate, noDescriptions);
        return TrainingResponse.from(trainings.get(current.id(), id));
    }

    /** El run recién encolado/lanzado es el más reciente de la lista. */
    private TrainingResponse latestOf(AuthenticatedUser current, Long listId) {
        return trainings.listByList(current.id(), listId, Paging.of(0, 1)).items().stream()
                .map(TrainingResponse::from)
                .findFirst()
                .orElseThrow();
    }
}
