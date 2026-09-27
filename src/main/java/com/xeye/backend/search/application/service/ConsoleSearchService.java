package com.xeye.backend.search.application.service;

import com.xeye.backend.list.application.port.in.ListQueryPort;
import com.xeye.backend.search.application.command.ConsoleSearchCommand;
import com.xeye.backend.search.application.port.in.ConsoleSearchUseCases;
import com.xeye.backend.search.application.port.out.SearchQueryGateway;
import com.xeye.backend.search.application.port.out.SearchQueryGateway.SearchQueryResult;
import com.xeye.backend.shared.exception.NotFoundException;
import org.springframework.stereotype.Service;

/**
 * Playground de la consola. Comprueba la propiedad de la lista con la sesión del usuario y reenvía
 * la búsqueda al buscador por la API interna; el cupo por usuario lo aplica el propio buscador
 * (el 429 se propaga tal cual). Sin transacción: la llamada HTTP no debe retener una conexión.
 */
@Service
public class ConsoleSearchService implements ConsoleSearchUseCases {

    private final ListQueryPort lists;
    private final SearchQueryGateway gateway;

    public ConsoleSearchService(ListQueryPort lists, SearchQueryGateway gateway) {
        this.lists = lists;
        this.gateway = gateway;
    }

    @Override
    public SearchQueryResult search(Long userId, Long listId, ConsoleSearchCommand command) {
        lists.findById(listId)
                .filter(list -> list.userId().equals(userId))
                .orElseThrow(() -> new NotFoundException("List not found"));
        return gateway.search(listId, command);
    }
}
