package com.xeye.backend.training.infrastructure.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/** Blob de embeddings de un run (base64), fuera de {@code trainings} para que listar el historial no lo cargue. */
@Entity
@Table(name = "training_embeddings")
class TrainingEmbeddingsJpaEntity {

    @Id
    @Column(name = "training_id")
    private Long trainingId;

    @Column(name = "embeddings_data", nullable = false, columnDefinition = "longtext")
    private String embeddingsData;

    protected TrainingEmbeddingsJpaEntity() {
    }

    TrainingEmbeddingsJpaEntity(Long trainingId, String embeddingsData) {
        this.trainingId = trainingId;
        this.embeddingsData = embeddingsData;
    }

    Long getTrainingId() {
        return trainingId;
    }

    String getEmbeddingsData() {
        return embeddingsData;
    }
}
