package com.xeye.backend.list.infrastructure.persistence;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

interface ListJpaRepository extends JpaRepository<ListJpaEntity, Long> {

    List<ListJpaEntity> findByUserIdOrderByIdAsc(Long userId);

    Optional<ListJpaEntity> findByIdAndUserId(Long id, Long userId);
}
