package com.xeye.backend.training.application.service;

import com.xeye.backend.element.application.port.in.ElementQueryPort;
import com.xeye.backend.element.domain.model.Element;
import com.xeye.backend.list.application.port.in.ListQueryPort;
import com.xeye.backend.list.domain.model.ItemList;
import com.xeye.backend.shared.exception.ConflictException;
import com.xeye.backend.training.application.command.TrainingLaunchCommand;
import com.xeye.backend.training.application.port.out.SearchIndexer;
import com.xeye.backend.training.application.port.out.TrainingRepository;
import com.xeye.backend.training.config.TrainingProperties;
import com.xeye.backend.training.domain.model.Training;
import com.xeye.backend.training.domain.model.TrainingOption;
import com.xeye.backend.training.domain.model.TrainingStatus;
import org.junit.jupiter.api.Test;
import org.springframework.context.ApplicationEventPublisher;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * La cola de entrenamientos: una lista no puede tener dos runs a la vez (en cola o lanzados) y el
 * despachador respeta los cupos global y por usuario con equidad entre usuarios.
 */
class TrainingServiceLaunchGuardTest {

    private static final long USER_ID = 7L;
    private static final long OTHER_USER = 8L;
    private static final long LIST_ID = 3L;
    private static final long TRAINING_ID = 11L;

    private final TrainingRepository trainings = mock(TrainingRepository.class);
    private final ListQueryPort lists = mock(ListQueryPort.class);
    private final ElementQueryPort elements = mock(ElementQueryPort.class);
    private final SearchIndexer searchIndexer = mock(SearchIndexer.class);
    private final ApplicationEventPublisher events = mock(ApplicationEventPublisher.class);

    private TrainingService service(int maxConcurrent, int maxPerUser) {
        TrainingProperties properties = new TrainingProperties(
                "mock", "secret", "http://localhost:8000", 0,
                List.of("model-a", "model-b"), 30, maxConcurrent, maxPerUser, 90,
                new TrainingProperties.Pricing(0.0, 0.0057), null, null);
        return new TrainingService(trainings, lists, elements, searchIndexer, properties, events);
    }

    static Training training(long id, long listId, long userId, TrainingStatus status) {
        return new Training(id, listId, userId, null, status, null,
                null, null, false, null, null, null, null, false, null, 0L, null, null);
    }

    private static Training queued(long id, long userId) {
        Training t = training(id, id * 10, userId, TrainingStatus.PENDING);
        t.markQueued(List.of(new TrainingOption("embedding_model", "model-a")));
        return t;
    }

    @Test
    void enqueueRejectsWhenTheListAlreadyHasARunInProgress() {
        when(trainings.findByIdAndUserId(TRAINING_ID, USER_ID))
                .thenReturn(Optional.of(training(TRAINING_ID, LIST_ID, USER_ID, TrainingStatus.PENDING)));
        when(trainings.existsInProgressByListId(LIST_ID)).thenReturn(true);

        ConflictException ex = assertThrows(ConflictException.class,
                () -> service(5, 1).enqueue(TRAINING_ID, USER_ID, null, false, false));

        assertTrue(ex.getMessage().contains("queued or in progress"));
        verify(trainings, never()).save(any());
    }

    @Test
    void enqueueStoresTheOptionsAndMovesToQueued() {
        Training pending = training(TRAINING_ID, LIST_ID, USER_ID, TrainingStatus.PENDING);
        when(trainings.findByIdAndUserId(TRAINING_ID, USER_ID)).thenReturn(Optional.of(pending));
        when(trainings.existsInProgressByListId(LIST_ID)).thenReturn(false);
        when(elements.countByListId(LIST_ID)).thenReturn(3L);
        when(trainings.save(any())).thenAnswer(inv -> inv.getArgument(0));

        Training queued = service(5, 1).enqueue(TRAINING_ID, USER_ID, "model-b", true, false);

        assertEquals(TrainingStatus.QUEUED, queued.status());
        assertTrue(queued.options().contains(new TrainingOption("embedding_model", "model-b")));
        assertTrue(queued.options().contains(new TrainingOption("force_enrich", true)));
    }

    @Test
    void pickNextQueuedReturnsNothingWhenTheGlobalCapIsReached() {
        when(trainings.findQueued()).thenReturn(List.of(queued(1L, USER_ID)));
        when(trainings.countLaunched()).thenReturn(2L);

        assertTrue(service(2, 1).pickNextQueued().isEmpty());
    }

    @Test
    void pickNextQueuedSkipsUsersAtTheirPerUserCapAndPrefersTheOneWithFewestRuns() {
        // El usuario 7 ya tiene un run lanzado; el 8 ninguno: aunque el 7 encoló antes, va el 8.
        when(trainings.findQueued()).thenReturn(List.of(queued(1L, USER_ID), queued(2L, OTHER_USER)));
        when(trainings.countLaunched()).thenReturn(1L);
        when(trainings.countLaunchedByUser()).thenReturn(Map.of(USER_ID, 1L));

        Optional<Training> next = service(5, 1).pickNextQueued();

        assertTrue(next.isPresent());
        assertEquals(2L, next.get().id());
    }

    @Test
    void pickNextQueuedFallsBackToOldestWhenUsersAreEven() {
        when(trainings.findQueued()).thenReturn(List.of(queued(5L, OTHER_USER), queued(2L, USER_ID)));
        when(trainings.countLaunched()).thenReturn(0L);
        when(trainings.countLaunchedByUser()).thenReturn(Map.of());

        assertEquals(2L, service(5, 1).pickNextQueued().orElseThrow().id());
    }

    @Test
    void nonPositiveCapsDisableTheLimits() {
        when(trainings.findQueued()).thenReturn(List.of(queued(1L, USER_ID)));
        when(trainings.countLaunchedByUser()).thenReturn(Map.of(USER_ID, 10L));

        assertTrue(service(0, 0).pickNextQueued().isPresent());
        verify(trainings, never()).countLaunched();
    }

    @Test
    void prepareLaunchBuildsThePayloadFromTheQueuedOptionsAndUntrainsTheList() {
        Training queued = queued(TRAINING_ID, USER_ID);
        when(trainings.findById(TRAINING_ID)).thenReturn(Optional.of(queued));
        when(lists.findById(queued.listId())).thenReturn(Optional.of(
                new ItemList(queued.listId(), "list", "desc", false, USER_ID, null, null)));
        when(elements.findByListId(queued.listId())).thenReturn(List.of(
                new Element(21L, queued.listId(), "text", null, null, null, true, null, null)));
        when(trainings.save(any())).thenAnswer(inv -> inv.getArgument(0));

        TrainingLaunchCommand command = service(2, 1).prepareLaunch(TRAINING_ID);

        assertEquals(TRAINING_ID, command.trainingId());
        assertEquals(List.of(21L), queued.elementIds());
        assertTrue(command.options().contains(new TrainingOption("embedding_model", "model-a")));
        verify(elements).markAllTrained(queued.listId(), false);
    }

    @Test
    void prepareLaunchRejectsATrainingThatIsNotQueued() {
        when(trainings.findById(TRAINING_ID))
                .thenReturn(Optional.of(training(TRAINING_ID, LIST_ID, USER_ID, TrainingStatus.COMPLETED)));

        assertThrows(ConflictException.class, () -> service(2, 1).prepareLaunch(TRAINING_ID));
        verify(elements, never()).markAllTrained(any(), any(Boolean.class));
    }

    @Test
    void ensurePendingForLaunchRejectsWithoutCreatingARowWhileATrainingRuns() {
        when(lists.findById(LIST_ID)).thenReturn(Optional.of(
                new ItemList(LIST_ID, "list", "desc", false, USER_ID, null, null)));
        when(trainings.existsInProgressByListId(LIST_ID)).thenReturn(true);

        assertThrows(ConflictException.class,
                () -> service(5, 1).ensurePendingForLaunch(LIST_ID, USER_ID, null));

        verify(trainings, never()).save(any());
    }
}
