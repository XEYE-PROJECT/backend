package com.xeye.backend.list.infrastructure.persistence;

import org.springframework.data.domain.Limit;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

interface ListJpaRepository extends JpaRepository<ListJpaEntity, Long> {

    Optional<ListJpaEntity> findByIdAndUserId(Long id, Long userId);

    List<ListJpaEntity> findByIdGreaterThanOrderByIdAsc(Long afterId, Limit limit);

    @Query("""
            select l from ListJpaEntity l
            where l.userId = :userId
              and (:isPublic is null or l.isPublic = :isPublic)
              and (:query is null or lower(l.name) like lower(concat('%', :query, '%'))
                   or lower(l.description) like lower(concat('%', :query, '%')))
            order by l.id asc""")
    Page<ListJpaEntity> search(@Param("userId") Long userId, @Param("query") String query,
                               @Param("isPublic") Boolean isPublic, Pageable pageable);

    /** Conteo de elementos por lista en una sola consulta (el módulo list no depende del módulo element). */
    @Query(value = "select list_id, count(*) from elements where list_id in (:listIds) group by list_id",
            nativeQuery = true)
    List<Object[]> countElementsByListIds(@Param("listIds") Collection<Long> listIds);
}
