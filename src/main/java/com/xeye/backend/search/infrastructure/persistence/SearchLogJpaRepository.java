package com.xeye.backend.search.infrastructure.persistence;

import org.springframework.data.domain.Limit;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

interface SearchLogJpaRepository extends JpaRepository<SearchLogJpaEntity, Long> {

    List<SearchLogJpaEntity> findByListIdOrderByIdDesc(Long listId, Limit limit);
}
