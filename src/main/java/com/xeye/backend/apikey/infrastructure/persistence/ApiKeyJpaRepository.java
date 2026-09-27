package com.xeye.backend.apikey.infrastructure.persistence;

import org.springframework.data.domain.Limit;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

interface ApiKeyJpaRepository extends JpaRepository<ApiKeyJpaEntity, Long> {

    Page<ApiKeyJpaEntity> findByUserIdOrderByIdAsc(Long userId, Pageable pageable);

    List<ApiKeyJpaEntity> findByIdGreaterThanOrderByIdAsc(Long afterId, Limit limit);

    Optional<ApiKeyJpaEntity> findByIdAndUserId(Long id, Long userId);

    boolean existsByKeyHash(String keyHash);
}
