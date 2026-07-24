package com.xeye.backend.element.infrastructure.persistence;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

interface ElementJpaRepository extends JpaRepository<ElementJpaEntity, Long> {

    List<ElementJpaEntity> findByListIdOrderByIdAsc(Long listId);

    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("update ElementJpaEntity e set e.trained = :trained where e.listId = :listId")
    void updateTrainedByListId(@Param("listId") Long listId, @Param("trained") boolean trained);

    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("""
            update ElementJpaEntity e set e.generatedDescription = :generatedDescription
            where e.id = :elementId and e.listId = :listId""")
    void updateGeneratedDescription(@Param("listId") Long listId,
                                    @Param("elementId") Long elementId,
                                    @Param("generatedDescription") String generatedDescription);
}
