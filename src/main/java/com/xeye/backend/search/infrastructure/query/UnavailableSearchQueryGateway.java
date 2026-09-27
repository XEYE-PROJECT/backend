package com.xeye.backend.search.infrastructure.query;

import com.xeye.backend.search.application.command.ConsoleSearchCommand;
import com.xeye.backend.search.application.port.out.SearchQueryGateway;
import com.xeye.backend.shared.exception.ServiceUnavailableException;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/**
 * Provider {@code log} (dev sin buscador): el playground responde 503 con un mensaje claro en
 * vez de simular resultados. Para probarlo en local, arrancar {@code ../search-service} y poner
 * {@code SEARCH_PROVIDER=http}.
 */
@Component
@ConditionalOnProperty(name = "xeye.search.provider", havingValue = "log", matchIfMissing = true)
public class UnavailableSearchQueryGateway implements SearchQueryGateway {

    @Override
    public SearchQueryResult search(Long listId, ConsoleSearchCommand command) {
        throw new ServiceUnavailableException(
                "Search service is not configured (SEARCH_PROVIDER=log); start the search-service and set SEARCH_PROVIDER=http");
    }
}
