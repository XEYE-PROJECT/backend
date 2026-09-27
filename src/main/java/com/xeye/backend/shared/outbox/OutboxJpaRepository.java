package com.xeye.backend.shared.outbox;

import org.springframework.data.domain.Limit;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;

interface OutboxJpaRepository extends JpaRepository<OutboxJpaEntity, Long> {

    List<OutboxJpaEntity> findByStatusAndAvailableAtLessThanEqualOrderByIdAsc(String status, Instant now, Limit limit);

    long countByStatus(String status);

    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("""
            update OutboxJpaEntity e set e.attempts = :attempts, e.availableAt = :availableAt,
                e.lastError = :error where e.id = :id""")
    void reschedule(@Param("id") Long id, @Param("attempts") int attempts,
                    @Param("availableAt") Instant availableAt, @Param("error") String error);

    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("""
            update OutboxJpaEntity e set e.status = :status, e.attempts = :attempts, e.lastError = :error
            where e.id = :id""")
    void markStatus(@Param("id") Long id, @Param("status") String status, @Param("attempts") int attempts,
                    @Param("error") String error);
}
