package com.xeye.backend.search.application.port.out;

import com.xeye.backend.search.domain.model.SearchLog;
import com.xeye.backend.shared.paging.Page;
import com.xeye.backend.shared.paging.Paging;

import java.time.Instant;

public interface SearchLogRepository {

    /** Persiste una entrada en su propia transacción (la ingesta por lotes aísla los fallos). */
    void save(SearchLog log);

    /** Página del historial de la lista, los más recientes primero. */
    Page<SearchLog> findByListId(Long listId, Paging paging);

    /** Borra hasta {@code batchSize} entradas buscadas antes del corte; devuelve cuántas borró. */
    int deleteSearchedBefore(Instant cutoff, int batchSize);
}
