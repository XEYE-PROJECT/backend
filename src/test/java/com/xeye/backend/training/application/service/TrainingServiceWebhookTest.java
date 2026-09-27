package com.xeye.backend.training.application.service;

import com.xeye.backend.element.application.port.in.ElementQueryPort;
import com.xeye.backend.list.application.port.in.ListQueryPort;
import com.xeye.backend.shared.event.SearchIndexRequestedEvent;
import com.xeye.backend.shared.exception.BadRequestException;
import com.xeye.backend.training.application.command.TrainingUpdateCommand;
import com.xeye.backend.training.application.port.out.SearchIndexer;
import com.xeye.backend.training.application.port.out.TrainingRepository;
import com.xeye.backend.training.config.TrainingProperties;
import com.xeye.backend.training.domain.model.Training;
import com.xeye.backend.training.domain.model.TrainingStatus;
import org.junit.jupiter.api.Test;
import org.springframework.context.ApplicationEventPublisher;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** Webhook idempotente: duplicados y callbacks fuera de orden se ignoran; el push a búsqueda sale por el outbox. */
class TrainingServiceWebhookTest {

    private static final long USER_ID = 7L;
    private static final long LIST_ID = 3L;
    private static final long TRAINING_ID = 11L;

    private final TrainingRepository trainings = mock(TrainingRepository.class);
    private final ListQueryPort lists = mock(ListQueryPort.class);
    private final ElementQueryPort elements = mock(ElementQueryPort.class);
    private final SearchIndexer searchIndexer = mock(SearchIndexer.class);
    private final ApplicationEventPublisher events = mock(ApplicationEventPublisher.class);

    private TrainingService service() {
        TrainingProperties properties = new TrainingProperties(
                "mock", "secret", "http://localhost:8000", 0,
                List.of("model-a"), 30, 1, 1, 90,
                new TrainingProperties.Pricing(0.3, 0.005), null, null);
        return new TrainingService(trainings, lists, elements, searchIndexer, properties, events);
    }

    private static Training launched() {
        Training t = TrainingServiceLaunchGuardTest.training(TRAINING_ID, LIST_ID, USER_ID, TrainingStatus.PENDING);
        t.markQueued(List.of());
        t.markLaunched("job");
        return t;
    }

    private static TrainingUpdateCommand update(String status, String embeddings) {
        return new TrainingUpdateCommand(TRAINING_ID, LIST_ID, status, embeddings, "model", null, null, null,
                Map.of(21L, "{\"desc\":\"x\"}"), 1);
    }

    @Test
    void completedStoresEmbeddingsApartAndEnqueuesTheSearchPush() {
        Training training = launched();
        when(trainings.findById(TRAINING_ID)).thenReturn(Optional.of(training));
        when(trainings.save(any())).thenAnswer(inv -> inv.getArgument(0));

        assertTrue(service().applyUpdate(update("completed", "ZW1i")));

        verify(trainings).clearInUseForList(LIST_ID);
        verify(elements).saveGeneratedDescriptions(eq(LIST_ID), any());
        verify(elements).markAllTrained(LIST_ID, true);
        verify(trainings).saveEmbeddings(TRAINING_ID, "ZW1i");
        verify(events).publishEvent(new SearchIndexRequestedEvent(LIST_ID, TRAINING_ID));
        // La llamada HTTP nunca corre dentro de la transacción del webhook.
        verify(searchIndexer, never()).index(any());
        assertEquals(TrainingStatus.COMPLETED, training.status());
    }

    @Test
    void aDuplicateCompletedIsIgnored() {
        Training training = launched();
        training.markCompleted(true, "model", null, null, null, null);
        when(trainings.findById(TRAINING_ID)).thenReturn(Optional.of(training));

        assertFalse(service().applyUpdate(update("completed", "ZW1i")));

        verify(trainings, never()).saveEmbeddings(anyLong(), anyString());
        verify(events, never()).publishEvent(any());
    }

    @Test
    void aProgressCallbackAfterCompletionIsIgnored() {
        Training training = launched();
        training.markCompleted(true, "model", null, null, null, null);
        when(trainings.findById(TRAINING_ID)).thenReturn(Optional.of(training));

        assertFalse(service().applyUpdate(update("optimizing", null)));
        verify(trainings, never()).save(any());
    }

    @Test
    void aRepeatedProgressCallbackIsAHeartbeatAndIsSaved() {
        Training training = launched();
        training.applyProgress(TrainingStatus.OPTIMIZING);
        when(trainings.findById(TRAINING_ID)).thenReturn(Optional.of(training));
        when(trainings.save(any())).thenAnswer(inv -> inv.getArgument(0));

        assertTrue(service().applyUpdate(update("optimizing", null)));
        verify(trainings).save(training);
    }

    @Test
    void aListMismatchIsRejected() {
        when(trainings.findById(TRAINING_ID)).thenReturn(Optional.of(launched()));
        TrainingUpdateCommand wrongList = new TrainingUpdateCommand(TRAINING_ID, 999L, "optimizing", null, null,
                null, null, null, Map.of(), null);

        BadRequestException ex = assertThrows(BadRequestException.class, () -> service().applyUpdate(wrongList));
        assertEquals("TRAINING_LIST_MISMATCH", ex.code());
    }

    @Test
    void completedWithoutEmbeddingsIsRejected() {
        when(trainings.findById(TRAINING_ID)).thenReturn(Optional.of(launched()));

        BadRequestException ex = assertThrows(BadRequestException.class,
                () -> service().applyUpdate(update("completed", null)));
        assertEquals("MISSING_EMBEDDINGS", ex.code());
    }

    @Test
    void pushToSearchLoadsEmbeddingsAndSkipsInactiveTrainings() {
        Training training = launched();
        training.markCompleted(true, "model", null, null, null, null);
        training.deactivate();
        when(trainings.findById(TRAINING_ID)).thenReturn(Optional.of(training));

        service().pushToSearch(TRAINING_ID);

        verify(searchIndexer, never()).index(any());
    }
}
