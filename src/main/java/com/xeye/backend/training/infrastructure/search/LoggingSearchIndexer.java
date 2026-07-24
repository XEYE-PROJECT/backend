package com.xeye.backend.training.infrastructure.search;

import com.xeye.backend.training.application.command.SearchIndexCommand;
import com.xeye.backend.training.application.port.out.SearchIndexer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/** Indexador por defecto (dev): loguea lo que empujaría en vez de llamar al servicio de búsqueda. */
@Component
@ConditionalOnProperty(name = "xeye.search.provider", havingValue = "log", matchIfMissing = true)
public class LoggingSearchIndexer implements SearchIndexer {

    private static final Logger log = LoggerFactory.getLogger(LoggingSearchIndexer.class);

    @Override
    public void index(SearchIndexCommand command) {
        log.info("[search:log] would push list {} ('{}', public={}) with {} elements and {} chars of embeddings",
                command.listId(), command.listName(), command.isPublic(), command.elements().size(),
                command.embeddingsData() == null ? 0 : command.embeddingsData().length());
    }
}
