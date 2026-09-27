package com.xeye.backend.element.infrastructure.persistence;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

interface ElementJpaRepository extends JpaRepository<ElementJpaEntity, Long> {

    List<ElementJpaEntity> findByListIdOrderByIdAsc(Long listId);

    long countByListId(Long listId);

    @Query("""
            select e from ElementJpaEntity e
            where e.listId = :listId
              and (:query is null or lower(e.text) like lower(concat('%', :query, '%'))
                   or lower(e.description) like lower(concat('%', :query, '%')))
            order by e.id asc""")
    Page<ElementJpaEntity> search(@Param("listId") Long listId, @Param("query") String query, Pageable pageable);

    List<ElementJpaEntity> findByIdInOrderByIdAsc(List<Long> ids);

    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("update ElementJpaEntity e set e.trained = :trained, e.version = e.version + 1 where e.listId = :listId")
    void updateTrainedByListId(@Param("listId") Long listId, @Param("trained") boolean trained);
}
