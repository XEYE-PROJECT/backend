package com.xeye.backend.search.infrastructure.sync;

import com.xeye.backend.search.application.port.out.SearchSyncNotifier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/** Notificador por defecto (dev): loguea lo que enviaría en vez de llamar al servicio de búsqueda. */
@Component
@ConditionalOnProperty(name = "xeye.search.provider", havingValue = "log", matchIfMissing = true)
public class LoggingSearchSyncNotifier implements SearchSyncNotifier {

    private static final Logger log = LoggerFactory.getLogger(LoggingSearchSyncNotifier.class);

    @Override
    public void listMetaChanged(Long listId, Long userId, String name, boolean isPublic) {
        log.info("[search:log] would notify list {} meta changed ('{}', public={})", listId, name, isPublic);
    }

    @Override
    public void listDeleted(Long listId) {
        log.info("[search:log] would notify list {} deleted", listId);
    }

    @Override
    public void listDataInvalidated(Long listId) {
        log.info("[search:log] would notify list {} data invalidated", listId);
    }

    @Override
    public void apiKeyCreated(Long apiKeyId, Long userId, String apiKey) {
        log.info("[search:log] would notify api key {} created for user {}", apiKeyId, userId);
    }

    @Override
    public void apiKeyDeleted(Long apiKeyId) {
        log.info("[search:log] would notify api key {} deleted", apiKeyId);
    }

    @Override
    public void userDeleted(Long userId) {
        log.info("[search:log] would notify user {} deleted", userId);
    }
}
