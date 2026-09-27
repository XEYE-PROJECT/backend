package com.xeye.backend.element.infrastructure.persistence;

import com.xeye.backend.element.application.port.out.ElementRepository;
import com.xeye.backend.element.domain.model.Element;
import com.xeye.backend.shared.paging.Page;
import com.xeye.backend.shared.paging.Paging;
import jakarta.persistence.EntityManager;
import org.springframework.data.domain.PageRequest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.stereotype.Component;

import java.sql.PreparedStatement;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Persistencia de elementos. Las escrituras masivas (importación, enriquecimientos del worker)
 * van por JDBC en lotes: con ids {@code AUTO_INCREMENT} Hibernate no puede agrupar los INSERT,
 * y con {@code rewriteBatchedStatements=true} el driver convierte cada lote en un único
 * INSERT multi-fila.
 */
@Component
public class ElementPersistenceAdapter implements ElementRepository {

    static final int BATCH_SIZE = 500;

    private static final String INSERT_SQL = """
            insert into elements (list_id, text, params, description, generated_description, trained, version)
            values (?, ?, ?, ?, ?, ?, 0)""";
    private static final String UPDATE_GENERATED_SQL = """
            update elements set generated_description = ?, version = version + 1
            where id = ? and list_id = ?""";

    private final ElementJpaRepository jpa;
    private final JdbcTemplate jdbc;
    private final EntityManager entityManager;

    public ElementPersistenceAdapter(ElementJpaRepository jpa, JdbcTemplate jdbc, EntityManager entityManager) {
        this.jpa = jpa;
        this.jdbc = jdbc;
        this.entityManager = entityManager;
    }

    @Override
    public List<Element> findByListId(Long listId) {
        return jpa.findByListIdOrderByIdAsc(listId).stream().map(ElementMapper::toDomain).toList();
    }

    @Override
    public Page<Element> findByListId(Long listId, String query, Paging paging) {
        var result = jpa.search(listId, query, PageRequest.of(paging.pageNumber(), paging.limit()));
        return new Page<>(result.getContent().stream().map(ElementMapper::toDomain).toList(),
                result.getTotalElements(), paging.offset(), paging.limit());
    }

    @Override
    public long countByListId(Long listId) {
        return jpa.countByListId(listId);
    }

    @Override
    public Optional<Element> findById(Long id) {
        return jpa.findById(id).map(ElementMapper::toDomain);
    }

    @Override
    public Element save(Element element) {
        return ElementMapper.toDomain(jpa.save(ElementMapper.toEntity(element)));
    }

    @Override
    public List<Element> saveAll(List<Element> elements) {
        if (elements.isEmpty()) {
            return List.of();
        }
        // Lo pendiente en el contexto JPA se escribe antes de tocar la tabla por JDBC.
        entityManager.flush();
        List<Long> ids = new ArrayList<>(elements.size());
        for (int from = 0; from < elements.size(); from += BATCH_SIZE) {
            List<Element> chunk = elements.subList(from, Math.min(from + BATCH_SIZE, elements.size()));
            ids.addAll(insertChunk(chunk));
        }
        entityManager.clear();
        return jpa.findByIdInOrderByIdAsc(ids).stream().map(ElementMapper::toDomain).toList();
    }

    private List<Long> insertChunk(List<Element> chunk) {
        KeyHolder keys = new GeneratedKeyHolder();
        jdbc.batchUpdate(connection -> connection.prepareStatement(INSERT_SQL, Statement.RETURN_GENERATED_KEYS),
                new org.springframework.jdbc.core.BatchPreparedStatementSetter() {
                    @Override
                    public void setValues(PreparedStatement ps, int i) throws java.sql.SQLException {
                        Element e = chunk.get(i);
                        ps.setLong(1, e.listId());
                        ps.setString(2, e.text());
                        ps.setString(3, e.params());
                        ps.setString(4, e.description());
                        ps.setString(5, e.generatedDescription());
                        ps.setBoolean(6, e.trained());
                    }

                    @Override
                    public int getBatchSize() {
                        return chunk.size();
                    }
                }, keys);
        List<Long> ids = new ArrayList<>(chunk.size());
        for (Map<String, Object> key : keys.getKeyList()) {
            Object value = key.values().iterator().next();
            ids.add(((Number) value).longValue());
        }
        if (ids.size() != chunk.size()) {
            throw new IllegalStateException("Batch insert returned " + ids.size() + " ids for "
                    + chunk.size() + " elements");
        }
        return ids;
    }

    @Override
    public void deleteById(Long id) {
        jpa.deleteById(id);
    }

    @Override
    public void updateTrainedByListId(Long listId, boolean trained) {
        jpa.updateTrainedByListId(listId, trained);
    }

    @Override
    public void updateGeneratedDescriptions(Long listId, Map<Long, String> byElementId) {
        if (byElementId.isEmpty()) {
            return;
        }
        entityManager.flush();
        List<Object[]> args = byElementId.entrySet().stream()
                .map(entry -> new Object[] {entry.getValue(), entry.getKey(), listId})
                .toList();
        for (int from = 0; from < args.size(); from += BATCH_SIZE) {
            jdbc.batchUpdate(UPDATE_GENERATED_SQL, args.subList(from, Math.min(from + BATCH_SIZE, args.size())));
        }
        entityManager.clear();
    }
}
