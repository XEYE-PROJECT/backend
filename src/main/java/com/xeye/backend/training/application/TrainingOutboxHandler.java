package com.xeye.backend.training.application;

import com.xeye.backend.shared.event.SearchIndexRequestedEvent;
import com.xeye.backend.shared.event.TrainingRequestedEvent;
import com.xeye.backend.shared.outbox.OutboxEvent;
import com.xeye.backend.shared.outbox.OutboxHandler;
import com.xeye.backend.training.application.port.in.TrainingLaunchService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import tools.jackson.databind.ObjectMapper;

import java.util.Set;

/**
 * Conecta, vía outbox, las ediciones de listas/elementos con el flujo de training (una edición
 * marca la lista con un training PENDING; el usuario decide cuándo lanzarlo) y los trainings
 * completados/activados con el push de su índice al buscador. Ambos se entregan tras el commit
 * del cambio que los produjo, con reintentos si fallan.
 */
@Component
public class TrainingOutboxHandler implements OutboxHandler {

    private static final Logger log = LoggerFactory.getLogger(TrainingOutboxHandler.class);

    private final TrainingLaunchService launchService;
    private final ObjectMapper json;

    public TrainingOutboxHandler(TrainingLaunchService launchService, ObjectMapper json) {
        this.launchService = launchService;
        this.json = json;
    }

    @Override
    public Set<String> types() {
        return Set.of(TrainingRequestedEvent.TYPE, SearchIndexRequestedEvent.TYPE);
    }

    @Override
    public void handle(OutboxEvent event) {
        switch (event.type()) {
            case TrainingRequestedEvent.TYPE -> {
                TrainingRequestedEvent e = json.readValue(event.payload(), TrainingRequestedEvent.class);
                launchService.ensurePending(e.listId(), e.userId());
                log.debug("Training request for list {} — {}", e.listId(), e.reason());
            }
            case SearchIndexRequestedEvent.TYPE -> {
                SearchIndexRequestedEvent e = json.readValue(event.payload(), SearchIndexRequestedEvent.class);
                launchService.pushToSearch(e.trainingId());
            }
            default -> throw new IllegalArgumentException("Unsupported outbox event type " + event.type());
        }
    }
}
