package com.xeye.backend.training.domain.model;

import java.time.Instant;
import java.util.List;
import java.util.Objects;

/**
 * Un training de una lista. Las transiciones las fija {@link TrainingStatus#canTransitionTo}:
 * los callbacks del worker solo avanzan, un estado repetido es un latido y un run terminado no
 * cambia (los webhooks son idempotentes). {@code inUse} marca el training cuyo modelo está
 * activo para la lista. Los embeddings viven en su propia tabla: aquí solo {@code hasEmbeddings}.
 * {@code lastHeartbeatAt} lo actualiza cada callback y lo vigila el barrido de estancados.
 */
public class Training {

    /** Mensaje con el que el barrido marca un run estancado; un {@code completed} tardío aún gana. */
    public static final String STALLED_ERROR = "The training stopped reporting progress and was marked as stalled";

    private final Long id;
    private final Long listId;
    private final Long userId;
    private String instanceId;
    private TrainingStatus status;
    private List<TrainingOption> options;
    /** Ids de elementos (ASC) capturados al lanzar: la fila i de la matriz de embeddings es de elementIds[i]. */
    private List<Long> elementIds;
    /** Elementos con descripción LLM (caché + generadas) al calcular los embeddings; puede ser < elementIds.size(). */
    private Integer describedCount;
    private boolean hasEmbeddings;
    private String model;
    private TrainingTime time;
    private TrainingCost cost;
    private String error;
    private boolean inUse;
    private Instant lastHeartbeatAt;
    private final Long version;
    private final Instant createdAt;
    private final Instant updatedAt;

    public Training(Long id, Long listId, Long userId, String instanceId, TrainingStatus status,
                    List<TrainingOption> options, List<Long> elementIds, Integer describedCount,
                    boolean hasEmbeddings, String model, TrainingTime time, TrainingCost cost,
                    String error, boolean inUse, Instant lastHeartbeatAt, Long version,
                    Instant createdAt, Instant updatedAt) {
        this.id = id;
        this.listId = Objects.requireNonNull(listId, "listId");
        this.userId = Objects.requireNonNull(userId, "userId");
        this.instanceId = instanceId;
        this.status = Objects.requireNonNull(status, "status");
        this.options = options;
        this.elementIds = elementIds;
        this.describedCount = describedCount;
        this.hasEmbeddings = hasEmbeddings;
        this.model = model;
        this.time = time;
        this.cost = cost;
        this.error = error;
        this.inUse = inUse;
        this.lastHeartbeatAt = lastHeartbeatAt;
        this.version = version;
        this.createdAt = createdAt;
        this.updatedAt = updatedAt;
    }

    /** Hubo una edición: la lista necesita reentrenar, pero el usuario decide cuándo (y con qué modelo). */
    public static Training pending(Long listId, Long userId) {
        return new Training(null, listId, userId, null, TrainingStatus.PENDING, null,
                null, null, false, null, null, null, null, false, null, null, null, null);
    }

    /** El usuario lanza este training pendiente: entra en cola con las opciones que fijan el run. */
    public void markQueued(List<TrainingOption> options) {
        require(TrainingStatus.QUEUED);
        this.options = options;
        this.status = TrainingStatus.QUEUED;
        this.lastHeartbeatAt = Instant.now();
    }

    /** Captura el conjunto de elementos en el lanzamiento (el orden de filas de la matriz de embeddings). */
    public void recordElementIds(List<Long> elementIds) {
        this.elementIds = elementIds;
    }

    /** Fija el precio preestablecido del run (fijo + descripciones a generar) al lanzarlo. */
    public void priceAtLaunch(TrainingCost cost) {
        this.cost = cost;
    }

    /**
     * El provider aceptó el run. Si el worker ya llamó (un callback puede adelantarse a esta
     * escritura), el estado no retrocede: solo se anota el id de instancia.
     */
    public void markLaunched(String instanceId) {
        this.instanceId = instanceId;
        this.lastHeartbeatAt = Instant.now();
        if (status == TrainingStatus.QUEUED) {
            this.status = TrainingStatus.INITIALIZED;
        }
    }

    /**
     * Aplica un callback de progreso del worker. Devuelve false si se ignora (regresión,
     * run terminado, estado no permitido); un estado repetido cuenta como latido.
     */
    public boolean applyProgress(TrainingStatus reported) {
        if (reported != TrainingStatus.OPTIMIZING && reported != TrainingStatus.TRAINING) {
            return false;
        }
        if (!status.canTransitionTo(reported)) {
            return false;
        }
        this.status = reported;
        this.lastHeartbeatAt = Instant.now();
        return true;
    }

    /** ¿Puede este run recibir un {@code completed}? (en marcha, o estancado por el barrido). */
    public boolean canComplete() {
        return status.canTransitionTo(TrainingStatus.COMPLETED) || wasStalled();
    }

    public boolean canFail() {
        return status.canTransitionTo(TrainingStatus.FAILED);
    }

    public boolean wasStalled() {
        return status == TrainingStatus.FAILED && STALLED_ERROR.equals(error);
    }

    public void markCompleted(boolean hasEmbeddings, String model, TrainingTime time, TrainingCost reportedCost,
                              Double actualEnrichmentCost, Integer describedCount) {
        if (!canComplete()) {
            throw new IllegalStateException("Training " + id + " cannot complete from status " + status.value());
        }
        this.status = TrainingStatus.COMPLETED;
        this.hasEmbeddings = hasEmbeddings;
        this.model = model;
        this.time = time;
        this.describedCount = describedCount;
        this.cost = mergeCost(this.cost, reportedCost, actualEnrichmentCost);
        this.error = null;
        this.inUse = true;
        this.lastHeartbeatAt = Instant.now();
    }

    public void markFailed(String error) {
        this.status = TrainingStatus.FAILED;
        this.error = error;
        this.inUse = false;
        this.cost = null; // un entrenamiento fallido no se cobra
        this.lastHeartbeatAt = Instant.now();
    }

    public void markStalled() {
        markFailed(STALLED_ERROR);
    }

    /**
     * El precio fijo lo preestablece el backend al lanzar; el de enriquecimiento se ajusta al
     * completar a las descripciones realmente generadas ({@code actualEnrichmentCost}): el worker
     * tolera fallos del LLM por elemento, así que puede devolver menos de las estimadas. Del
     * worker solo se toman los costes reales (cómputo y LLM, con sus tokens), que son informativos
     * y no entran en el precio. Sin precio preestablecido (trainings antiguos o el flujo mock sin
     * lanzamiento) vale lo que reporte el worker.
     */
    private static TrainingCost mergeCost(TrainingCost preset, TrainingCost reported, Double actualEnrichmentCost) {
        if (preset == null) {
            return reported;
        }
        double runpod = reported == null || reported.runpod() == null ? 0.0 : reported.runpod();
        double fixed = preset.fixed() == null ? 0.0 : preset.fixed();
        double enrichment = actualEnrichmentCost != null ? actualEnrichmentCost
                : preset.enrichment() == null ? 0.0 : preset.enrichment();
        double total = Math.round((runpod + fixed + enrichment) * 1e6) / 1e6;
        return new TrainingCost(runpod, reported == null ? null : reported.llm(), fixed, enrichment, total,
                reported == null ? null : reported.llmInputTokens(), reported == null ? null : reported.llmOutputTokens());
    }

    /** El usuario elige este training completado como el modelo activo de la lista. */
    public void activate() {
        this.inUse = true;
    }

    public void deactivate() {
        this.inUse = false;
    }

    /** Valor de una opción del run ({@code embedding_model}, {@code force_enrich}, {@code strategy}…), o null. */
    public Object option(String key) {
        if (options == null) {
            return null;
        }
        return options.stream().filter(o -> key.equals(o.key())).map(TrainingOption::value).findFirst().orElse(null);
    }

    private void require(TrainingStatus next) {
        if (!status.canTransitionTo(next)) {
            throw new IllegalStateException("Training " + id + " cannot go from " + status.value()
                    + " to " + next.value());
        }
    }

    public Long id() {
        return id;
    }

    public Long listId() {
        return listId;
    }

    public Long userId() {
        return userId;
    }

    public String instanceId() {
        return instanceId;
    }

    public TrainingStatus status() {
        return status;
    }

    public List<TrainingOption> options() {
        return options;
    }

    public List<Long> elementIds() {
        return elementIds;
    }

    public Integer describedCount() {
        return describedCount;
    }

    public boolean hasEmbeddings() {
        return hasEmbeddings;
    }

    public String model() {
        return model;
    }

    public TrainingTime time() {
        return time;
    }

    public TrainingCost cost() {
        return cost;
    }

    public String error() {
        return error;
    }

    public boolean inUse() {
        return inUse;
    }

    public Instant lastHeartbeatAt() {
        return lastHeartbeatAt;
    }

    public Long version() {
        return version;
    }

    public Instant createdAt() {
        return createdAt;
    }

    public Instant updatedAt() {
        return updatedAt;
    }
}
