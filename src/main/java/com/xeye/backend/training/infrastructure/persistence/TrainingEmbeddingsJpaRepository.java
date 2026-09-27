package com.xeye.backend.training.infrastructure.persistence;

import org.springframework.data.jpa.repository.JpaRepository;

interface TrainingEmbeddingsJpaRepository extends JpaRepository<TrainingEmbeddingsJpaEntity, Long> {
}
