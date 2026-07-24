package com.xeye.backend.search.application.port.out;

import com.xeye.backend.search.domain.model.SearchLog;

import java.util.List;

public interface SearchLogRepository {

    /** Persiste una entrada en su propia transacción (la ingesta por lotes aísla los fallos). */
    void save(SearchLog log);

    /** Los más recientes primero. */
    List<SearchLog> findByListId(Long listId, int limit);
}
