package com.xeye.backend.apikey.infrastructure.persistence;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

interface ApiKeyJpaRepository extends JpaRepository<ApiKeyJpaEntity, Long> {

    List<ApiKeyJpaEntity> findByUserIdOrderByIdAsc(Long userId);

    Optional<ApiKeyJpaEntity> findByIdAndUserId(Long id, Long userId);

    boolean existsByApiKey(String apiKey);
}
