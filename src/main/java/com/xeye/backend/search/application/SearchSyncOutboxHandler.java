package com.xeye.backend.search.application;

import com.xeye.backend.search.application.port.out.SearchSyncNotifier;
import com.xeye.backend.shared.event.ApiKeyCreatedEvent;
import com.xeye.backend.shared.event.ApiKeyDeletedEvent;
import com.xeye.backend.shared.event.ListDeletedEvent;
import com.xeye.backend.shared.event.ListElementsChangedEvent;
import com.xeye.backend.shared.event.ListMetaChangedEvent;
import com.xeye.backend.shared.event.UserDeletedEvent;
import com.xeye.backend.shared.event.UserSearchLimitChangedEvent;
import com.xeye.backend.shared.outbox.OutboxEvent;
import com.xeye.backend.shared.outbox.OutboxHandler;
import org.springframework.stereotype.Component;
import tools.jackson.databind.ObjectMapper;

import java.util.Set;

/**
 * Entrega al microservicio de búsqueda los eventos de dominio que le afectan, desde el outbox
 * (escritos en la transacción del cambio, entregados después con reintentos y backoff por el
 * {@code OutboxRelay}). Una excepción aquí = reintentar más tarde; el buscador se cura solo
 * recargando de la API interna, así que la entrega tardía solo significa un rato de desfase.
 */
@Component
public class SearchSyncOutboxHandler implements OutboxHandler {

    private final SearchSyncNotifier notifier;
    private final ObjectMapper json;

    public SearchSyncOutboxHandler(SearchSyncNotifier notifier, ObjectMapper json) {
        this.notifier = notifier;
        this.json = json;
    }

    @Override
    public Set<String> types() {
        return Set.of(ListMetaChangedEvent.TYPE, ListDeletedEvent.TYPE, ListElementsChangedEvent.TYPE,
                ApiKeyCreatedEvent.TYPE, ApiKeyDeletedEvent.TYPE, UserSearchLimitChangedEvent.TYPE,
                UserDeletedEvent.TYPE);
    }

    @Override
    public void handle(OutboxEvent event) {
        switch (event.type()) {
            case ListMetaChangedEvent.TYPE -> {
                ListMetaChangedEvent e = json.readValue(event.payload(), ListMetaChangedEvent.class);
                notifier.listMetaChanged(e.listId(), e.userId(), e.name(), e.isPublic());
            }
            case ListDeletedEvent.TYPE -> notifier.listDeleted(
                    json.readValue(event.payload(), ListDeletedEvent.class).listId());
            case ListElementsChangedEvent.TYPE -> notifier.listDataInvalidated(
                    json.readValue(event.payload(), ListElementsChangedEvent.class).listId());
            case ApiKeyCreatedEvent.TYPE -> {
                ApiKeyCreatedEvent e = json.readValue(event.payload(), ApiKeyCreatedEvent.class);
                notifier.apiKeyCreated(e.apiKeyId(), e.userId(), e.keyHash());
            }
            case ApiKeyDeletedEvent.TYPE -> notifier.apiKeyDeleted(
                    json.readValue(event.payload(), ApiKeyDeletedEvent.class).apiKeyId());
            case UserSearchLimitChangedEvent.TYPE -> {
                UserSearchLimitChangedEvent e = json.readValue(event.payload(), UserSearchLimitChangedEvent.class);
                notifier.userSearchLimitChanged(e.userId(), e.rateLimitPerMinute());
            }
            case UserDeletedEvent.TYPE -> notifier.userDeleted(
                    json.readValue(event.payload(), UserDeletedEvent.class).userId());
            default -> throw new IllegalArgumentException("Unsupported outbox event type " + event.type());
        }
    }
}
