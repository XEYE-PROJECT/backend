package com.xeye.backend.search.infrastructure.persistence;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;

interface SearchLogJpaRepository extends JpaRepository<SearchLogJpaEntity, Long> {

    Page<SearchLogJpaEntity> findByListIdOrderByIdDesc(Long listId, Pageable pageable);

    /** Nativo por el LIMIT: JPQL no admite borrar por lotes. */
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query(value = "delete from searches where searched_at < :cutoff limit :batchSize", nativeQuery = true)
    int deleteSearchedBefore(@Param("cutoff") Instant cutoff, @Param("batchSize") int batchSize);
}
