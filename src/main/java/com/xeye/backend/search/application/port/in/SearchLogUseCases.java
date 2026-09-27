package com.xeye.backend.search.application.port.in;

import com.xeye.backend.search.application.command.RecordSearchCommand;
import com.xeye.backend.search.domain.model.SearchLog;
import com.xeye.backend.shared.paging.Page;
import com.xeye.backend.shared.paging.Paging;

import java.util.List;

/** Puerto de entrada: ingesta de logs de búsqueda (API interna), su lectura por el propietario y la retención. */
public interface SearchLogUseCases {

    /**
     * Persiste un lote reportado por el microservicio de búsqueda y devuelve cuántas entradas
     * aceptó. Las inválidas se omiten: fallar el lote haría reintentar para siempre las válidas.
     */
    int recordAll(List<RecordSearchCommand> commands);

    /** Historial de una lista del usuario, los más recientes primero. */
    Page<SearchLog> listByList(Long userId, Long listId, Paging paging);

    /** Borra el historial más antiguo que la retención configurada; devuelve cuántas filas borró. */
    int purgeExpired();
}
