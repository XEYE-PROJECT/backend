package com.xeye.backend.training.infrastructure.persistence;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

interface TrainingJpaRepository extends JpaRepository<TrainingJpaEntity, Long> {

    List<TrainingJpaEntity> findByListIdAndUserIdOrderByIdDesc(Long listId, Long userId);

    Optional<TrainingJpaEntity> findByIdAndUserId(Long id, Long userId);

    Optional<TrainingJpaEntity> findFirstByListIdAndInUseTrueOrderByIdDesc(Long listId);

    Optional<TrainingJpaEntity> findFirstByListIdAndStatus(Long listId, String status);

    List<TrainingJpaEntity> findByUserIdAndStatusOrderByIdDesc(Long userId, String status);

    List<TrainingJpaEntity> findByStatusInAndUpdatedAtBefore(Collection<String> statuses, Instant cutoff);

    boolean existsByListIdAndStatusIn(Long listId, Collection<String> statuses);

    long countByStatusIn(Collection<String> statuses);

    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("update TrainingJpaEntity t set t.inUse = false where t.listId = :listId")
    void clearInUseForList(@Param("listId") Long listId);
}
