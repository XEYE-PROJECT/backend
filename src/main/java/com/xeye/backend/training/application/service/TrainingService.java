package com.xeye.backend.training.application.service;

import com.xeye.backend.element.application.port.in.ElementQueryPort;
import com.xeye.backend.element.domain.model.Element;
import com.xeye.backend.list.application.port.in.ListQueryPort;
import com.xeye.backend.list.domain.model.ItemList;
import com.xeye.backend.shared.exception.BadRequestException;
import com.xeye.backend.shared.exception.ConflictException;
import com.xeye.backend.shared.exception.NotFoundException;
import com.xeye.backend.training.application.command.SearchIndexCommand;
import com.xeye.backend.training.application.command.TrainingLaunchCommand;
import com.xeye.backend.training.application.command.TrainingUpdateCommand;
import com.xeye.backend.training.application.port.in.TrainingCompletionHandler;
import com.xeye.backend.training.application.port.in.TrainingLaunchService;
import com.xeye.backend.training.application.port.in.TrainingQueryPort;
import com.xeye.backend.training.application.port.in.TrainingUseCases;
import com.xeye.backend.training.application.port.out.SearchIndexer;
import com.xeye.backend.training.application.port.out.TrainingRepository;
import com.xeye.backend.training.config.TrainingProperties;
import com.xeye.backend.training.domain.model.Training;
import com.xeye.backend.training.domain.model.TrainingOption;
import com.xeye.backend.training.domain.model.TrainingStatus;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Orquesta el flujo de training:
 * <ol>
 *   <li>{@link #ensurePending} — una edición marca la lista como pendiente de reentrenar
 *       (un training PENDING por lista; nada se lanza solo).</li>
 *   <li>{@link #prepareLaunch} — el usuario lanza el pendiente: fija sus opciones (modelo de
 *       embedding), marca los elementos como no entrenados y monta el payload del worker.</li>
 *   <li>{@link #applyUpdate} — aplica los callbacks; al completar marca los elementos
 *       entrenados, activa este training ({@code in_use}) y empuja a búsqueda.</li>
 * </ol>
 */
@Service
public class TrainingService implements TrainingUseCases, TrainingLaunchService, TrainingCompletionHandler,
        TrainingQueryPort {

    private static final Logger log = LoggerFactory.getLogger(TrainingService.class);

    private final TrainingRepository trainings;
    private final ListQueryPort lists;
    private final ElementQueryPort elements;
    private final SearchIndexer searchIndexer;
    private final TrainingProperties properties;

    public TrainingService(TrainingRepository trainings, ListQueryPort lists, ElementQueryPort elements,
                           SearchIndexer searchIndexer, TrainingProperties properties) {
        this.trainings = trainings;
        this.lists = lists;
        this.elements = elements;
        this.searchIndexer = searchIndexer;
        this.properties = properties;
    }

    @Override
    @Transactional(readOnly = true)
    public List<ListedTraining> listByList(Long userId, Long listId) {
        List<Training> history = trainings.findByListIdAndUserId(listId, userId);
        if (history.isEmpty()) {
            return List.of();
        }
        Set<Long> currentElementIds = currentElementIds(listId);
        return history.stream()
                .map(training -> new ListedTraining(training, coversCurrentElements(training, currentElementIds)))
                .toList();
    }

    @Override
    @Transactional
    public Training use(Long userId, Long trainingId) {
        Training training = trainings.findByIdAndUserId(trainingId, userId)
                .orElseThrow(() -> new NotFoundException("Training not found"));
        if (training.inUse()) {
            return training;
        }
        if (!coversCurrentElements(training, currentElementIds(training.listId()))) {
            throw new ConflictException(
                    "Only a completed training with the same trained elements as the list can be put in use");
        }
        trainings.clearInUseForList(training.listId());
        training.activate();
        Training activated = trainings.save(training);
        // Los flags trained de los elementos no se tocan: siguen marcando qué elementos se
        // editaron desde su último embedding, sea cual sea el training activo.
        pushToSearch(activated);
        log.info("Training {} put in use for list {}", activated.id(), activated.listId());
        return activated;
    }

    private Set<Long> currentElementIds(Long listId) {
        return elements.findByListId(listId).stream().map(Element::id).collect(Collectors.toSet());
    }

    /** Elegible para {@code in_use}: completado y con exactamente los elementos actuales de la lista. */
    private boolean coversCurrentElements(Training training, Set<Long> currentElementIds) {
        return training.status() == TrainingStatus.COMPLETED
                && training.embeddingsData() != null
                && training.elementIds() != null
                && Set.copyOf(training.elementIds()).equals(currentElementIds);
    }

    @Override
    @Transactional(readOnly = true)
    public Training get(Long userId, Long trainingId) {
        return trainings.findByIdAndUserId(trainingId, userId)
                .orElseThrow(() -> new NotFoundException("Training not found"));
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<Training> findInUseByListId(Long listId) {
        return trainings.findInUseByListId(listId);
    }

    @Override
    public List<String> availableEmbeddingModels() {
        List<String> models = properties.embeddingModels();
        return models == null ? List.of() : models;
    }

    @Override
    @Transactional(readOnly = true)
    public List<Training> pendingForUser(Long userId) {
        return trainings.findPendingByUserId(userId);
    }

    @Override
    @Transactional
    public void ensurePending(Long listId, Long userId) {
        if (trainings.findPendingByListId(listId).isPresent()) {
            log.debug("List {} already has a pending training", listId);
            return;
        }
        if (lists.findById(listId).isEmpty()) {
            log.debug("List {} no longer exists; skipping pending training", listId);
            return;
        }
        try {
            Training pending = trainings.save(Training.pending(listId, userId));
            log.info("Training {} pending for list {}", pending.id(), listId);
        } catch (DataIntegrityViolationException ex) {
            // Ediciones concurrentes pasaron el check a la vez; el índice único sobre la columna
            // generada pending (V4) garantiza una sola fila — perder la carrera no es problema.
            log.debug("List {} got its pending training from a concurrent edit", listId);
        }
    }

    @Override
    @Transactional
    public Training ensurePendingForLaunch(Long listId, Long userId, String embeddingModel) {
        ItemList list = lists.findById(listId)
                .orElseThrow(() -> new NotFoundException("List not found"));
        if (!list.userId().equals(userId)) {
            throw new NotFoundException("List not found");
        }
        // Fallar antes de tocar la BD: un lanzamiento rechazado no debe dejar una fila pendiente huérfana.
        resolveEmbeddingModel(embeddingModel);
        assertLaunchCapacity(listId);
        if (elements.findByListId(listId).isEmpty()) {
            throw new BadRequestException("The list has no elements to train");
        }
        return trainings.findPendingByListId(listId)
                .orElseGet(() -> trainings.save(Training.pending(listId, userId)));
    }

    @Override
    @Transactional
    public TrainingLaunchCommand prepareLaunch(Long trainingId, Long userId, String embeddingModel) {
        Training training = trainings.findByIdAndUserId(trainingId, userId)
                .orElseThrow(() -> new NotFoundException("Training not found"));
        if (training.status() != TrainingStatus.PENDING) {
            throw new ConflictException("Only a pending training can be launched");
        }
        Long listId = training.listId();
        assertLaunchCapacity(listId);
        ItemList list = lists.findById(listId)
                .orElseThrow(() -> new NotFoundException("List not found"));
        List<Element> listElements = elements.findByListId(listId);
        if (listElements.isEmpty()) {
            throw new BadRequestException("The list has no elements to train");
        }

        List<TrainingOption> options = List.of(
                new TrainingOption("train_all", true),
                new TrainingOption("embedding_model", resolveEmbeddingModel(embeddingModel)));
        training.markQueued(options);
        // Se reentrena la lista entera, así que nada está "entrenado" hasta que complete.
        elements.markAllTrained(listId, false);

        // generatedDescription viaja con cada elemento: es la salida LLM previa del worker y es
        // null justo en los elementos cuyo texto/descripción cambió — solo esos pagan el LLM.
        List<TrainingLaunchCommand.ElementPayload> payload = listElements.stream()
                .map(e -> new TrainingLaunchCommand.ElementPayload(
                        e.id(), e.text(), e.description(), e.generatedDescription(), e.trained()))
                .toList();
        // Guardamos qué elementos (id ASC) embebará el worker: búsqueda alinea las filas de
        // embeddings con estos ids, sobreviviendo a ediciones concurrentes.
        training.recordElementIds(payload.stream().map(TrainingLaunchCommand.ElementPayload::id).toList());
        trainings.save(training);

        return new TrainingLaunchCommand(
                training.id(), listId, userId,
                properties.callbackUrl(), properties.webhookSecret(),
                new TrainingLaunchCommand.ListPayload(list.id(), list.name(), list.description()),
                payload, options);
    }

    /**
     * Reglas de admisión de un lanzamiento: la lista no puede tener otro run sin terminar, y el
     * total de runs del backend no puede superar {@code xeye.training.max-concurrent} (cada run
     * es un contenedor worker; el tope evita saturar la máquina). El orquestador serializa los
     * lanzamientos, así que dos peticiones concurrentes no pueden colarse por el mismo hueco.
     */
    private void assertLaunchCapacity(Long listId) {
        if (trainings.existsRunningByListId(listId)) {
            throw new ConflictException("The list already has a training in progress");
        }
        int max = properties.maxConcurrent();
        if (max > 0 && trainings.countRunning() >= max) {
            throw new ConflictException("The maximum number of concurrent trainings (" + max
                    + ") has been reached; try again when one finishes");
        }
    }

    private String resolveEmbeddingModel(String requested) {
        List<String> available = properties.embeddingModels();
        if (available == null || available.isEmpty()) {
            throw new IllegalStateException("No embedding models configured (xeye.training.embedding-models)");
        }
        if (requested == null || requested.isBlank()) {
            return available.get(0);
        }
        String model = requested.trim();
        if (!available.contains(model)) {
            throw new BadRequestException("Unknown embedding model: " + model);
        }
        return model;
    }

    @Override
    @Transactional
    public void markLaunched(Long trainingId, String instanceId) {
        trainings.findById(trainingId).ifPresent(training -> {
            training.markLaunched(instanceId);
            trainings.save(training);
        });
    }

    @Override
    @Transactional
    public void markFailed(Long trainingId, String error) {
        trainings.findById(trainingId).ifPresent(training -> {
            training.markFailed(error);
            trainings.save(training);
        });
    }

    @Override
    @Transactional
    public List<Training> failStalled(Instant cutoff) {
        List<Training> stalled = trainings.findRunningUpdatedBefore(cutoff);
        for (Training training : stalled) {
            log.warn("Training {} for list {} stalled in status {}; marking failed",
                    training.id(), training.listId(), training.status().value());
            training.markFailed("The training stopped reporting progress and was marked as stalled");
            trainings.save(training);
        }
        return stalled;
    }

    @Override
    @Transactional
    public void applyUpdate(TrainingUpdateCommand update) {
        Training training = trainings.findById(update.trainingId())
                .orElseThrow(() -> new NotFoundException("Training not found: " + update.trainingId()));
        TrainingStatus status = TrainingStatus.fromString(update.status());
        switch (status) {
            case OPTIMIZING -> {
                training.markOptimizing();
                trainings.save(training);
            }
            case TRAINING -> {
                training.markTraining();
                trainings.save(training);
            }
            case FAILED -> {
                training.markFailed(update.error());
                trainings.save(training);
            }
            case COMPLETED -> complete(training, update);
            default -> log.warn("Ignoring training update {} with non-callback status {}",
                    update.trainingId(), status);
        }
    }

    private void complete(Training training, TrainingUpdateCommand update) {
        Long listId = training.listId();
        // Primero los updates masivos (limpian el contexto de persistencia), después el save de la entidad.
        trainings.clearInUseForList(listId);
        if (update.generatedDescriptions() != null && !update.generatedDescriptions().isEmpty()) {
            elements.saveGeneratedDescriptions(listId, update.generatedDescriptions());
            log.debug("Cached {} LLM enrichments for list {}", update.generatedDescriptions().size(), listId);
        }
        elements.markAllTrained(listId, true);
        training.markCompleted(update.embeddingsData(), update.model(), update.time(), update.cost());
        trainings.save(training);
        pushToSearch(training);
        log.info("Training {} completed for list {}", training.id(), listId);
    }

    private void pushToSearch(Training training) {
        lists.findById(training.listId()).ifPresent(list -> {
            List<SearchIndexCommand.Element> payload = elements.findByListId(training.listId()).stream()
                    .map(this::toSearchElement)
                    .toList();
            try {
                searchIndexer.index(new SearchIndexCommand(
                        list.id(), list.userId(), list.name(), list.isPublic(),
                        training.embeddingsData(), training.model(), training.elementIds(), payload));
            } catch (Exception ex) {
                // Un fallo de búsqueda nunca debe revertir el training completado: el worker no
                // reintenta el webhook y búsqueda recarga perezosamente de todos modos.
                log.warn("Search index push failed for list {} (search will lazy-load): {}",
                        list.id(), ex.getMessage());
            }
        });
    }

    private SearchIndexCommand.Element toSearchElement(Element element) {
        return new SearchIndexCommand.Element(
                element.id(), element.text(), element.params(),
                element.description(), element.generatedDescription());
    }
}
