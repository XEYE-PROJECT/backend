package com.xeye.backend.search.infrastructure.persistence;

import com.xeye.backend.search.application.port.out.SearchLogRepository;
import com.xeye.backend.search.domain.model.SearchLog;
import org.springframework.data.domain.Limit;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Component
public class SearchLogPersistenceAdapter implements SearchLogRepository {

    private final SearchLogJpaRepository jpa;

    public SearchLogPersistenceAdapter(SearchLogJpaRepository jpa) {
        this.jpa = jpa;
    }

    @Override
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void save(SearchLog log) {
        jpa.save(SearchLogMapper.toEntity(log));
    }

    @Override
    public List<SearchLog> findByListId(Long listId, int limit) {
        return jpa.findByListIdOrderByIdDesc(listId, Limit.of(limit)).stream()
                .map(SearchLogMapper::toDomain)
                .toList();
    }
}
