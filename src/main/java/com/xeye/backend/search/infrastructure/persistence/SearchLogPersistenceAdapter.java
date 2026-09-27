package com.xeye.backend.search.infrastructure.persistence;

import com.xeye.backend.search.application.port.out.SearchLogRepository;
import com.xeye.backend.search.domain.model.SearchLog;
import com.xeye.backend.shared.paging.Page;
import com.xeye.backend.shared.paging.Paging;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;

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
    public Page<SearchLog> findByListId(Long listId, Paging paging) {
        var result = jpa.findByListIdOrderByIdDesc(listId, PageRequest.of(paging.pageNumber(), paging.limit()));
        return new Page<>(result.getContent().stream().map(SearchLogMapper::toDomain).toList(),
                result.getTotalElements(), paging.offset(), paging.limit());
    }

    @Override
    @Transactional
    public int deleteSearchedBefore(Instant cutoff, int batchSize) {
        return jpa.deleteSearchedBefore(cutoff, batchSize);
    }
}
