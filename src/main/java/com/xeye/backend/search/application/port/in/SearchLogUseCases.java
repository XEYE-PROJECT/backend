package com.xeye.backend.search.application.port.in;

import com.xeye.backend.search.application.command.RecordSearchCommand;
import com.xeye.backend.search.domain.model.SearchLog;

import java.util.List;

/** Puerto de entrada: ingesta de logs de búsqueda (API interna) y su lectura por el propietario (REST). */
public interface SearchLogUseCases {

    /**
     * Persiste un lote reportado por el microservicio de búsqueda y devuelve cuántas entradas
     * aceptó. Las inválidas se omiten: fallar el lote haría reintentar para siempre las válidas.
     */
    int recordAll(List<RecordSearchCommand> commands);

    /** Últimos logs de una lista del usuario, los más recientes primero. */
    List<SearchLog> listByList(Long userId, Long listId, int limit);
}
