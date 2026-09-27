package com.xeye.backend.training.infrastructure.persistence;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

interface TrainingJpaRepository extends JpaRepository<TrainingJpaEntity, Long> {

    Page<TrainingJpaEntity> findByListIdAndUserIdOrderByIdDesc(Long listId, Long userId, Pageable pageable);

    Optional<TrainingJpaEntity> findByIdAndUserId(Long id, Long userId);

    Optional<TrainingJpaEntity> findFirstByListIdAndInUseTrueOrderByIdDesc(Long listId);

    Optional<TrainingJpaEntity> findFirstByListIdAndStatus(Long listId, String status);

    List<TrainingJpaEntity> findByUserIdAndStatusOrderByIdDesc(Long userId, String status);

    List<TrainingJpaEntity> findByStatusOrderByIdAsc(String status);

    /** Latido nulo = run lanzado antes de existir la columna: cae a updated_at. */
    @Query("""
            select t from TrainingJpaEntity t
            where t.status in :statuses and coalesce(t.lastHeartbeatAt, t.updatedAt) < :cutoff""")
    List<TrainingJpaEntity> findStalled(@Param("statuses") Collection<String> statuses, @Param("cutoff") Instant cutoff);

    boolean existsByListIdAndStatusIn(Long listId, Collection<String> statuses);

    long countByStatusIn(Collection<String> statuses);

    long countByStatusAndIdLessThan(String status, Long id);

    @Query("select t.userId, count(t) from TrainingJpaEntity t where t.status in :statuses group by t.userId")
    List<Object[]> countByUserIdWhereStatusIn(@Param("statuses") Collection<String> statuses);

    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("update TrainingJpaEntity t set t.inUse = false, t.version = t.version + 1 where t.listId = :listId")
    void clearInUseForList(@Param("listId") Long listId);

    /** Nativo por el LIMIT; los embeddings caen por la FK ON DELETE CASCADE. */
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query(value = """
            delete from trainings where status in ('completed', 'failed') and in_use = false
              and updated_at < :cutoff limit :batchSize""", nativeQuery = true)
    int deleteTerminalNotInUseBefore(@Param("cutoff") Instant cutoff, @Param("batchSize") int batchSize);
}
