package com.xeye.backend.training.domain.model;

import java.time.Instant;
import java.util.List;
import java.util.Objects;

/**
 * Un training de una lista. Las transiciones son laxas a propósito (los webhooks pueden llegar
 * desordenados); gana la última escritura. {@code inUse} marca el training cuyo modelo está
 * activo para la lista — se fija al completar y se limpia cuando completa uno más nuevo.
 */
public class Training {

    private final Long id;
    private final Long listId;
    private final Long userId;
    private String instanceId;
    private TrainingStatus status;
    private List<TrainingOption> options;
    /** Ids de elementos (ASC) capturados al lanzar: la fila i de la matriz de embeddings es de elementIds[i]. */
    private List<Long> elementIds;
    private String embeddingsData;
    private String model;
    private TrainingTime time;
    private TrainingCost cost;
    private String error;
    private boolean inUse;
    private final Instant createdAt;
    private final Instant updatedAt;

    public Training(Long id, Long listId, Long userId, String instanceId, TrainingStatus status,
                    List<TrainingOption> options, List<Long> elementIds, String embeddingsData, String model,
                    TrainingTime time, TrainingCost cost, String error, boolean inUse,
                    Instant createdAt, Instant updatedAt) {
        this.id = id;
        this.listId = Objects.requireNonNull(listId, "listId");
        this.userId = Objects.requireNonNull(userId, "userId");
        this.instanceId = instanceId;
        this.status = Objects.requireNonNull(status, "status");
        this.options = options;
        this.elementIds = elementIds;
        this.embeddingsData = embeddingsData;
        this.model = model;
        this.time = time;
        this.cost = cost;
        this.error = error;
        this.inUse = inUse;
        this.createdAt = createdAt;
        this.updatedAt = updatedAt;
    }

    /** Hubo una edición: la lista necesita reentrenar, pero el usuario decide cuándo (y con qué modelo). */
    public static Training pending(Long listId, Long userId) {
        return new Training(null, listId, userId, null, TrainingStatus.PENDING, null,
                null, null, null, null, null, null, false, null, null);
    }

    /** El usuario lanza este training pendiente; las opciones fijan el run (modelo, train_all…). */
    public void markQueued(List<TrainingOption> options) {
        this.options = options;
        this.status = TrainingStatus.QUEUED;
    }

    /** Captura el conjunto de elementos en el lanzamiento (el orden de filas de la matriz de embeddings). */
    public void recordElementIds(List<Long> elementIds) {
        this.elementIds = elementIds;
    }

    public void markLaunched(String instanceId) {
        this.instanceId = instanceId;
        this.status = TrainingStatus.INITIALIZED;
    }

    public void markOptimizing() {
        this.status = TrainingStatus.OPTIMIZING;
    }

    public void markTraining() {
        this.status = TrainingStatus.TRAINING;
    }

    public void markCompleted(String embeddingsData, String model, TrainingTime time, TrainingCost cost) {
        this.status = TrainingStatus.COMPLETED;
        this.embeddingsData = embeddingsData;
        this.model = model;
        this.time = time;
        this.cost = cost;
        this.error = null;
        this.inUse = true;
    }

    public void markFailed(String error) {
        this.status = TrainingStatus.FAILED;
        this.error = error;
        this.inUse = false;
    }

    /** El usuario elige este training completado como el modelo activo de la lista. */
    public void activate() {
        this.inUse = true;
    }

    public void deactivate() {
        this.inUse = false;
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

    public String embeddingsData() {
        return embeddingsData;
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

    public Instant createdAt() {
        return createdAt;
    }

    public Instant updatedAt() {
        return updatedAt;
    }
}
