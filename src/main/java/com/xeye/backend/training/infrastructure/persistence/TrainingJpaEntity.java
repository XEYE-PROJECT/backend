package com.xeye.backend.training.infrastructure.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import org.hibernate.annotations.Generated;
import org.hibernate.generator.EventType;

import java.time.Instant;

/**
 * Mapeo JPA de {@code trainings}. Las columnas JSON (options/time/cost) y los dos blobs grandes
 * (embeddings_data/model) se guardan como strings — el adapter (de)serializa las estructuradas
 * con Jackson. {@code `time`} va entrecomillado por ser palabra reservada.
 */
@Entity
@Table(name = "trainings")
public class TrainingJpaEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "list_id", nullable = false)
    private Long listId;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(name = "instance_id", length = 255)
    private String instanceId;

    @Column(nullable = false, length = 30)
    private String status;

    @Column(columnDefinition = "json")
    private String options;

    @Column(name = "element_ids", columnDefinition = "json")
    private String elementIds;

    @Column(name = "described_count")
    private Integer describedCount;

    @Column(name = "embeddings_data", columnDefinition = "longtext")
    private String embeddingsData;

    @Column(columnDefinition = "longtext")
    private String model;

    @Column(name = "`time`", columnDefinition = "json")
    private String time;

    @Column(columnDefinition = "json")
    private String cost;

    @Column(columnDefinition = "text")
    private String error;

    @Column(name = "in_use", nullable = false)
    private boolean inUse;

    @Generated(event = EventType.INSERT)
    @Column(name = "created_at", insertable = false, updatable = false)
    private Instant createdAt;

    @Generated(event = {EventType.INSERT, EventType.UPDATE})
    @Column(name = "updated_at", insertable = false, updatable = false)
    private Instant updatedAt;

    protected TrainingJpaEntity() {
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public Long getListId() {
        return listId;
    }

    public void setListId(Long listId) {
        this.listId = listId;
    }

    public Long getUserId() {
        return userId;
    }

    public void setUserId(Long userId) {
        this.userId = userId;
    }

    public String getInstanceId() {
        return instanceId;
    }

    public void setInstanceId(String instanceId) {
        this.instanceId = instanceId;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public String getOptions() {
        return options;
    }

    public void setOptions(String options) {
        this.options = options;
    }

    public String getElementIds() {
        return elementIds;
    }

    public void setElementIds(String elementIds) {
        this.elementIds = elementIds;
    }

    public Integer getDescribedCount() {
        return describedCount;
    }

    public void setDescribedCount(Integer describedCount) {
        this.describedCount = describedCount;
    }

    public String getEmbeddingsData() {
        return embeddingsData;
    }

    public void setEmbeddingsData(String embeddingsData) {
        this.embeddingsData = embeddingsData;
    }

    public String getModel() {
        return model;
    }

    public void setModel(String model) {
        this.model = model;
    }

    public String getTime() {
        return time;
    }

    public void setTime(String time) {
        this.time = time;
    }

    public String getCost() {
        return cost;
    }

    public void setCost(String cost) {
        this.cost = cost;
    }

    public String getError() {
        return error;
    }

    public void setError(String error) {
        this.error = error;
    }

    public boolean isInUse() {
        return inUse;
    }

    public void setInUse(boolean inUse) {
        this.inUse = inUse;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }
}
