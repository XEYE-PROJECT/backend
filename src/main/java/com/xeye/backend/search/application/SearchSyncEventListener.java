package com.xeye.backend.search.application;

import com.xeye.backend.search.application.port.out.SearchSyncNotifier;
import com.xeye.backend.shared.event.ApiKeyCreatedEvent;
import com.xeye.backend.shared.event.ApiKeyDeletedEvent;
import com.xeye.backend.shared.event.ListDeletedEvent;
import com.xeye.backend.shared.event.ListElementsChangedEvent;
import com.xeye.backend.shared.event.ListMetaChangedEvent;
import com.xeye.backend.shared.event.UserDeletedEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * Reenvía al microservicio de búsqueda los eventos de dominio que le afectan. Corre en
 * asíncrono tras el commit (mismo patrón que {@code TrainingEventListener}), así que un
 * servicio lento o caído nunca afecta a la petición del usuario. Los fallos se loguean y
 * se descartan: búsqueda se cura sola recargando desde la API interna de sincronización.
 */
@Component
public class SearchSyncEventListener {

    private static final Logger log = LoggerFactory.getLogger(SearchSyncEventListener.class);

    private final SearchSyncNotifier notifier;

    public SearchSyncEventListener(SearchSyncNotifier notifier) {
        this.notifier = notifier;
    }

    @Async("searchSyncTaskExecutor")
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onListMetaChanged(ListMetaChangedEvent event) {
        notify("list meta changed", () ->
                notifier.listMetaChanged(event.listId(), event.userId(), event.name(), event.isPublic()));
    }

    @Async("searchSyncTaskExecutor")
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onListDeleted(ListDeletedEvent event) {
        notify("list deleted", () -> notifier.listDeleted(event.listId()));
    }

    @Async("searchSyncTaskExecutor")
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onListElementsChanged(ListElementsChangedEvent event) {
        notify("list elements changed", () -> notifier.listDataInvalidated(event.listId()));
    }

    @Async("searchSyncTaskExecutor")
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onApiKeyCreated(ApiKeyCreatedEvent event) {
        notify("api key created", () ->
                notifier.apiKeyCreated(event.apiKeyId(), event.userId(), event.apiKey()));
    }

    @Async("searchSyncTaskExecutor")
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onApiKeyDeleted(ApiKeyDeletedEvent event) {
        notify("api key deleted", () -> notifier.apiKeyDeleted(event.apiKeyId()));
    }

    @Async("searchSyncTaskExecutor")
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onUserDeleted(UserDeletedEvent event) {
        notify("user deleted", () -> notifier.userDeleted(event.userId()));
    }

    private void notify(String what, Runnable call) {
        try {
            call.run();
        } catch (Exception ex) {
            log.warn("Search sync notification failed ({}): {}", what, ex.getMessage());
        }
    }
}
