package com.xeye.backend.training.application.service;

import com.xeye.backend.element.application.port.in.ElementQueryPort;
import com.xeye.backend.element.domain.model.Element;
import com.xeye.backend.list.application.port.in.ListQueryPort;
import com.xeye.backend.list.domain.model.ItemList;
import com.xeye.backend.shared.event.SearchIndexRequestedEvent;
import com.xeye.backend.shared.exception.BadRequestException;
import com.xeye.backend.shared.exception.ConflictException;
import com.xeye.backend.shared.exception.NotFoundException;
import com.xeye.backend.shared.paging.Page;
import com.xeye.backend.shared.paging.Paging;
import com.xeye.backend.shared.security.WebhookTokens;
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
import com.xeye.backend.training.domain.model.TrainingCost;
import com.xeye.backend.training.domain.model.TrainingOption;
import com.xeye.backend.training.domain.model.TrainingStatus;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Orquesta el flujo de training:
 * <ol>
 *   <li>{@link #ensurePending} — una edición marca la lista como pendiente de reentrenar
 *       (un training PENDING por lista; nada se lanza solo).</li>
 *   <li>{@link #enqueue} — el usuario lanza el pendiente: fija sus opciones (modelo de
 *       embedding…) y lo pone en cola.</li>
 *   <li>{@link #pickNextQueued} + {@link #prepareLaunch} — el despachador elige el siguiente que
 *       cabe en el cupo (con equidad por usuario), marca los elementos como no entrenados y
 *       monta el payload del worker.</li>
 *   <li>{@link #applyUpdate} — aplica los callbacks (idempotentes, solo hacia delante); al
 *       completar guarda los embeddings aparte, marca los elementos entrenados, activa este
 *       training ({@code in_use}) y encola el push a búsqueda por el outbox.</li>
 * </ol>
 * Ninguna llamada HTTP saliente corre dentro de una transacción: el push al buscador lo hace el
 * outbox ({@link #pushToSearch}) y el lanzamiento el despachador, entre transacciones.
 */
@Service
public class TrainingService implements TrainingUseCases, TrainingLaunchService, TrainingCompletionHandler,
        TrainingQueryPort {

    private static final Logger log = LoggerFactory.getLogger(TrainingService.class);
    static final int PURGE_BATCH = 200;
    private static final int MAX_PURGE_BATCHES_PER_RUN = 50;

    private final TrainingRepository trainings;
    private final ListQueryPort lists;
    private final ElementQueryPort elements;
    private final SearchIndexer searchIndexer;
    private final TrainingProperties properties;
    private final ApplicationEventPublisher events;

    public TrainingService(TrainingRepository trainings, ListQueryPort lists, ElementQueryPort elements,
                           SearchIndexer searchIndexer, TrainingProperties properties,
                           ApplicationEventPublisher events) {
        this.trainings = trainings;
        this.lists = lists;
        this.elements = elements;
        this.searchIndexer = searchIndexer;
        this.properties = properties;
        this.events = events;
    }

    // ---- Lecturas ----

    @Override
    @Transactional(readOnly = true)
    public Page<ListedTraining> listByList(Long userId, Long listId, Paging paging) {
        Page<Training> history = trainings.findByListIdAndUserId(listId, userId, paging);
        if (history.items().isEmpty()) {
            return history.map(t -> new ListedTraining(t, false, null));
        }
        Set<Long> currentElementIds = currentElementIds(listId);
        return history.map(training -> listed(training, currentElementIds));
    }

    @Override
    @Transactional(readOnly = true)
    public ListedTraining get(Long userId, Long trainingId) {
        Training training = trainings.findByIdAndUserId(trainingId, userId)
                .orElseThrow(() -> new NotFoundException("Training not found"));
        return listed(training, currentElementIds(training.listId()));
    }

    private ListedTraining listed(Training training, Set<Long> currentElementIds) {
        Integer position = training.status() == TrainingStatus.QUEUED ? trainings.queuePosition(training.id()) : null;
        return new ListedTraining(training, coversCurrentElements(training, currentElementIds), position);
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
        Training fresh = trainings.findById(trainingId).orElseThrow(() -> new NotFoundException("Training not found"));
        fresh.activate();
        Training activated = trainings.save(fresh);
        // Los flags trained de los elementos no se tocan: siguen marcando qué elementos se
        // editaron desde su último embedding, sea cual sea el training activo.
        events.publishEvent(new SearchIndexRequestedEvent(activated.listId(), activated.id()));
        log.info("Training {} put in use for list {}", activated.id(), activated.listId());
        return activated;
    }

    @Override
    @Transactional(readOnly = true)
    public CostEstimate estimateCost(Long userId, Long listId, boolean regenerateDescriptions,
                                     boolean noDescriptions) {
        ItemList list = lists.findById(listId)
                .orElseThrow(() -> new NotFoundException("List not found"));
        if (!list.userId().equals(userId)) {
            throw new NotFoundException("List not found");
        }
        return presetCost(elements.findByListId(listId), regenerateDescriptions,
                noDescriptions || !list.llmEnrichment());
    }

    /**
     * Precio preestablecido de entrenar estos elementos ahora mismo: el fijo por entrenamiento
     * más uno por cada descripción LLM a generar (los elementos sin enriquecimiento cacheado,
     * o todos si el usuario pide regenerarlas; ninguna si entrena sin descripciones IA).
     * El backend es la única fuente de precios: esto mismo se fija en el training al lanzarlo.
     */
    private CostEstimate presetCost(List<Element> listElements, boolean regenerateDescriptions,
                                    boolean noDescriptions) {
        int descriptionsToGenerate = noDescriptions
                ? 0
                : regenerateDescriptions
                        ? listElements.size()
                        : (int) listElements.stream()
                                .filter(element -> element.generatedDescription() == null)
                                .count();
        TrainingProperties.Pricing pricing = properties.pricing();
        double fixed = Math.max(0, pricing.fixed());
        double enrichment = descriptionsToGenerate * Math.max(0, pricing.perDescription());
        return new CostEstimate(descriptionsToGenerate, fixed, enrichment, fixed + enrichment);
    }

    private Set<Long> currentElementIds(Long listId) {
        return elements.findByListId(listId).stream().map(Element::id).collect(Collectors.toSet());
    }

    /** Elegible para {@code in_use}: completado, con embeddings y con exactamente los elementos actuales de la lista. */
    private boolean coversCurrentElements(Training training, Set<Long> currentElementIds) {
        return training.status() == TrainingStatus.COMPLETED
                && training.hasEmbeddings()
                && training.elementIds() != null
                && Set.copyOf(training.elementIds()).equals(currentElementIds);
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<Training> findInUseByListId(Long listId) {
        return trainings.findInUseByListId(listId);
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<String> findEmbeddingsData(Long trainingId) {
        return trainings.findEmbeddings(trainingId);
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

    // ---- Pendiente y cola ----

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
        assertNoRunInProgress(listId);
        if (elements.countByListId(listId) == 0) {
            throw new BadRequestException("The list has no elements to train");
        }
        return trainings.findPendingByListId(listId)
                .orElseGet(() -> trainings.save(Training.pending(listId, userId)));
    }

    @Override
    @Transactional
    public Training enqueue(Long trainingId, Long userId, String embeddingModel,
                            boolean regenerateDescriptions, boolean noDescriptions) {
        Training training = trainings.findByIdAndUserId(trainingId, userId)
                .orElseThrow(() -> new NotFoundException("Training not found"));
        if (training.status() != TrainingStatus.PENDING) {
            throw new ConflictException("Only a pending training can be launched");
        }
        Long listId = training.listId();
        assertNoRunInProgress(listId);
        if (elements.countByListId(listId) == 0) {
            throw new BadRequestException("The list has no elements to train");
        }
        // Una lista que renunció al LLM (opt-out) se entrena siempre sin descripciones IA, pida
        // lo que pida el lanzamiento.
        boolean skipLlm = noDescriptions || !lists.findById(listId).map(ItemList::llmEnrichment).orElse(true);
        // force_enrich es la opción que el worker ya entiende (steps/enrich.py): con ella ignora
        // el enriquecimiento cacheado y regenera las descripciones LLM de todos los elementos,
        // devolviéndolas en el webhook (que las re-cachea). Con noDescriptions se envía en su
        // lugar strategy=embeddings_only (strategies.py: sin paso LLM) y force_enrich a false.
        List<TrainingOption> options = skipLlm
                ? List.of(
                        new TrainingOption("train_all", true),
                        new TrainingOption("embedding_model", resolveEmbeddingModel(embeddingModel)),
                        new TrainingOption("force_enrich", false),
                        new TrainingOption("strategy", "embeddings_only"))
                : List.of(
                        new TrainingOption("train_all", true),
                        new TrainingOption("embedding_model", resolveEmbeddingModel(embeddingModel)),
                        new TrainingOption("force_enrich", regenerateDescriptions));
        training.markQueued(options);
        Training queued = trainings.save(training);
        log.info("Training {} queued for list {}", queued.id(), listId);
        return queued;
    }

    /** Una lista no admite dos runs a la vez (en cola o lanzados). */
    private void assertNoRunInProgress(Long listId) {
        if (trainings.existsInProgressByListId(listId)) {
            throw new ConflictException("The list already has a training queued or in progress");
        }
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<Training> pickNextQueued() {
        List<Training> queued = trainings.findQueued();
        if (queued.isEmpty()) {
            return Optional.empty();
        }
        int max = properties.maxConcurrent();
        if (max > 0 && trainings.countLaunched() >= max) {
            return Optional.empty();
        }
        int perUser = properties.maxConcurrentPerUser();
        Map<Long, Long> launchedByUser = trainings.countLaunchedByUser();
        // Equidad: el usuario con menos runs en marcha va primero; a igualdad, el más antiguo.
        return queued.stream()
                .filter(t -> perUser <= 0 || launchedByUser.getOrDefault(t.userId(), 0L) < perUser)
                .min(Comparator.<Training>comparingLong(t -> launchedByUser.getOrDefault(t.userId(), 0L))
                        .thenComparingLong(Training::id));
    }

    @Override
    @Transactional
    public TrainingLaunchCommand prepareLaunch(Long trainingId) {
        Training training = trainings.findById(trainingId)
                .orElseThrow(() -> new NotFoundException("Training not found"));
        if (training.status() != TrainingStatus.QUEUED) {
            throw new ConflictException("Only a queued training can be launched");
        }
        Long listId = training.listId();
        ItemList list = lists.findById(listId)
                .orElseThrow(() -> new NotFoundException("List not found"));
        List<Element> listElements = elements.findByListId(listId);
        if (listElements.isEmpty()) {
            throw new BadRequestException("The list has no elements to train");
        }
        // El opt-out de la lista manda aunque haya cambiado después de encolar: el worker
        // recibe llm_enrichment=false en el job y no llama al LLM, y aquí no se cobra ninguna descripción.
        boolean noDescriptions = "embeddings_only".equals(training.option("strategy")) || !list.llmEnrichment();
        boolean regenerate = Boolean.TRUE.equals(training.option("force_enrich"));

        // Se reentrena la lista entera, así que nada está "entrenado" hasta que complete.
        elements.markAllTrained(listId, false);

        // generatedDescription viaja con cada elemento: es la salida LLM previa del worker y es
        // null justo en los elementos cuyo texto/descripción cambió — solo esos pagan el LLM
        // (todos, si force_enrich).
        List<TrainingLaunchCommand.ElementPayload> payload = listElements.stream()
                .map(e -> new TrainingLaunchCommand.ElementPayload(
                        e.id(), e.text(), e.description(), e.generatedDescription(), e.trained()))
                .toList();
        // Guardamos qué elementos (id ASC) embebará el worker: búsqueda alinea las filas de
        // embeddings con estos ids, sobreviviendo a ediciones concurrentes.
        training.recordElementIds(payload.stream().map(TrainingLaunchCommand.ElementPayload::id).toList());
        // El precio queda fijado aquí, antes de que el worker toque nada: al completar solo se
        // le suma el coste de cómputo reportado.
        CostEstimate preset = presetCost(listElements, regenerate, noDescriptions);
        training.priceAtLaunch(new TrainingCost(null, preset.fixed(), preset.enrichment(), preset.total()));
        trainings.save(training);

        // Token por entrenamiento: el worker lo devuelve en X-Webhook-Token y solo vale para este run.
        return new TrainingLaunchCommand(
                training.id(), listId, training.userId(),
                properties.callbackUrl(),
                WebhookTokens.issue(properties.webhookSecret(), training.id()),
                new TrainingLaunchCommand.ListPayload(list.id(), list.name(), list.description(), list.llmEnrichment()),
                payload, training.options());
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
            if (training.status().isTerminal()) {
                return;
            }
            training.markFailed(error);
            trainings.save(training);
        });
    }

    @Override
    @Transactional
    public List<Training> failStalled(Instant cutoff) {
        List<Training> stalled = trainings.findLaunchedWithHeartbeatBefore(cutoff);
        for (Training training : stalled) {
            log.warn("Training {} for list {} stalled in status {} (last heartbeat {}); marking failed",
                    training.id(), training.listId(), training.status().value(), training.lastHeartbeatAt());
            training.markStalled();
            trainings.save(training);
        }
        return stalled;
    }

    // ---- Callbacks ----

    @Override
    @Transactional
    public boolean applyUpdate(TrainingUpdateCommand update) {
        Training training = trainings.findById(update.trainingId())
                .orElseThrow(() -> new NotFoundException("Training not found: " + update.trainingId()));
        if (update.listId() != null && !update.listId().equals(training.listId())) {
            throw new BadRequestException("Training " + training.id() + " does not belong to list "
                    + update.listId(), "TRAINING_LIST_MISMATCH");
        }
        TrainingStatus status = TrainingStatus.fromString(update.status());
        switch (status) {
            case OPTIMIZING, TRAINING -> {
                if (!training.applyProgress(status)) {
                    return ignored(training, status);
                }
                trainings.save(training);
                return true;
            }
            case FAILED -> {
                if (!training.canFail()) {
                    return ignored(training, status);
                }
                training.markFailed(update.error());
                trainings.save(training);
                return true;
            }
            case COMPLETED -> {
                if (!training.canComplete()) {
                    return ignored(training, status);
                }
                complete(training, update);
                return true;
            }
            default -> throw new BadRequestException("Status '" + status.value()
                    + "' is not a valid worker callback", "INVALID_CALLBACK_STATUS");
        }
    }

    private boolean ignored(Training training, TrainingStatus reported) {
        log.info("Ignoring callback {} for training {} in status {} (duplicate, late or out of order)",
                reported.value(), training.id(), training.status().value());
        return false;
    }

    private void complete(Training training, TrainingUpdateCommand update) {
        Long listId = training.listId();
        if (update.embeddingsData() == null || update.embeddingsData().isBlank()) {
            throw new BadRequestException("A completed callback must carry embeddings_data", "MISSING_EMBEDDINGS");
        }
        // Primero los updates masivos (limpian el contexto de persistencia), después el save de la entidad.
        trainings.clearInUseForList(listId);
        if (update.generatedDescriptions() != null && !update.generatedDescriptions().isEmpty()) {
            elements.saveGeneratedDescriptions(listId, update.generatedDescriptions());
            log.debug("Cached {} LLM enrichments for list {}", update.generatedDescriptions().size(), listId);
        }
        elements.markAllTrained(listId, true);
        // El enriquecimiento se cobra por lo realmente generado: el worker tolera fallos del LLM
        // por elemento, así que pueden volver menos descripciones de las estimadas al lanzar.
        int generated = update.generatedDescriptions() == null ? 0 : update.generatedDescriptions().size();
        double enrichmentCost = Math.round(generated * Math.max(0, properties.pricing().perDescription()) * 1e6) / 1e6;
        // clearInUseForList subió la versión de esta fila: se relee para no chocar con el bloqueo optimista.
        Training fresh = trainings.findById(training.id())
                .orElseThrow(() -> new NotFoundException("Training not found: " + training.id()));
        fresh.markCompleted(true, update.model(), update.time(), update.cost(), enrichmentCost, update.describedCount());
        Training saved = trainings.save(fresh);
        trainings.saveEmbeddings(saved.id(), update.embeddingsData());
        events.publishEvent(new SearchIndexRequestedEvent(listId, saved.id()));
        log.info("Training {} completed for list {}", saved.id(), listId);
    }

    // ---- Push a búsqueda (desde el outbox, fuera de transacción) ----

    @Override
    public void pushToSearch(Long trainingId) {
        SearchIndexCommand command = buildIndexCommand(trainingId);
        if (command == null) {
            return;
        }
        searchIndexer.index(command);
    }

    /** Solo lecturas (cada una en su propia transacción corta); la llamada HTTP queda fuera. */
    private SearchIndexCommand buildIndexCommand(Long trainingId) {
        Training training = trainings.findById(trainingId).orElse(null);
        if (training == null || !training.inUse() || training.status() != TrainingStatus.COMPLETED) {
            log.debug("Training {} is no longer the active model; skipping search push", trainingId);
            return null;
        }
        ItemList list = lists.findById(training.listId()).orElse(null);
        if (list == null) {
            return null;
        }
        String embeddings = trainings.findEmbeddings(trainingId).orElse(null);
        if (embeddings == null) {
            log.warn("Training {} has no stored embeddings; skipping search push", trainingId);
            return null;
        }
        List<SearchIndexCommand.Element> payload = elements.findByListId(list.id()).stream()
                .map(this::toSearchElement)
                .toList();
        return new SearchIndexCommand(list.id(), list.userId(), list.name(), list.isPublic(),
                embeddings, training.model(), training.elementIds(), payload);
    }

    private SearchIndexCommand.Element toSearchElement(Element element) {
        return new SearchIndexCommand.Element(
                element.id(), element.text(), element.params(),
                element.description(), element.generatedDescription());
    }

    // ---- Retención ----

    @Override
    public int purgeExpired() {
        int days = properties.retentionDays();
        if (days <= 0) {
            return 0;
        }
        Instant cutoff = Instant.now().minus(Duration.ofDays(days));
        int total = 0;
        for (int i = 0; i < MAX_PURGE_BATCHES_PER_RUN; i++) {
            int deleted = trainings.deleteTerminalNotInUseBefore(cutoff, PURGE_BATCH);
            total += deleted;
            if (deleted < PURGE_BATCH) {
                break;
            }
        }
        if (total > 0) {
            log.info("Purged {} finished training(s) older than {} days", total, days);
        }
        return total;
    }
}
