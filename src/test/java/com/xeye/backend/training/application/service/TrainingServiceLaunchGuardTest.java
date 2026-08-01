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
import com.xeye.backend.training.domain.model.TrainingStatus;
import org.junit.jupiter.api.Test;

import java.util.List;
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
 * Cupos de lanzamiento: una lista no puede tener dos runs a la vez y el backend entero no puede
 * superar {@code xeye.training.max-concurrent} runs paralelos.
 */
class TrainingServiceLaunchGuardTest {

    private static final long USER_ID = 7L;
    private static final long LIST_ID = 3L;
    private static final long TRAINING_ID = 11L;

    private final TrainingRepository trainings = mock(TrainingRepository.class);
    private final ListQueryPort lists = mock(ListQueryPort.class);
    private final ElementQueryPort elements = mock(ElementQueryPort.class);
    private final SearchIndexer searchIndexer = mock(SearchIndexer.class);

    private TrainingService service(int maxConcurrent) {
        TrainingProperties properties = new TrainingProperties(
                "mock", "secret", "http://localhost:8000", 0,
                List.of("model-a", "model-b"), 30, maxConcurrent, null, null);
        return new TrainingService(trainings, lists, elements, searchIndexer, properties);
    }

    private Training pendingTraining() {
        return new Training(TRAINING_ID, LIST_ID, USER_ID, null, TrainingStatus.PENDING, null,
                null, null, null, null, null, null, false, null, null);
    }

    @Test
    void prepareLaunchRejectsWhenTheListAlreadyHasARunningTraining() {
        when(trainings.findByIdAndUserId(TRAINING_ID, USER_ID)).thenReturn(Optional.of(pendingTraining()));
        when(trainings.existsRunningByListId(LIST_ID)).thenReturn(true);

        ConflictException ex = assertThrows(ConflictException.class,
                () -> service(5).prepareLaunch(TRAINING_ID, USER_ID, null));

        assertTrue(ex.getMessage().contains("already has a training in progress"));
        verify(trainings, never()).save(any());
    }

    @Test
    void prepareLaunchRejectsWhenTheGlobalConcurrencyCapIsReached() {
        when(trainings.findByIdAndUserId(TRAINING_ID, USER_ID)).thenReturn(Optional.of(pendingTraining()));
        when(trainings.existsRunningByListId(LIST_ID)).thenReturn(false);
        when(trainings.countRunning()).thenReturn(2L);

        ConflictException ex = assertThrows(ConflictException.class,
                () -> service(2).prepareLaunch(TRAINING_ID, USER_ID, null));

        assertTrue(ex.getMessage().contains("maximum number of concurrent trainings (2)"));
        verify(trainings, never()).save(any());
    }

    @Test
    void prepareLaunchProceedsWhileUnderBothCaps() {
        when(trainings.findByIdAndUserId(TRAINING_ID, USER_ID)).thenReturn(Optional.of(pendingTraining()));
        when(trainings.existsRunningByListId(LIST_ID)).thenReturn(false);
        when(trainings.countRunning()).thenReturn(1L);
        when(lists.findById(LIST_ID)).thenReturn(Optional.of(
                new ItemList(LIST_ID, "list", "desc", false, USER_ID, null, null)));
        when(elements.findByListId(LIST_ID)).thenReturn(List.of(
                new Element(21L, LIST_ID, "text", null, null, null, true, null, null)));

        TrainingLaunchCommand command = service(2).prepareLaunch(TRAINING_ID, USER_ID, "model-b");

        assertEquals(TRAINING_ID, command.trainingId());
        assertEquals(LIST_ID, command.listId());
        verify(elements).markAllTrained(LIST_ID, false);
    }

    @Test
    void nonPositiveMaxConcurrentDisablesTheGlobalCap() {
        when(trainings.findByIdAndUserId(TRAINING_ID, USER_ID)).thenReturn(Optional.of(pendingTraining()));
        when(trainings.existsRunningByListId(LIST_ID)).thenReturn(false);
        when(lists.findById(LIST_ID)).thenReturn(Optional.of(
                new ItemList(LIST_ID, "list", "desc", false, USER_ID, null, null)));
        when(elements.findByListId(LIST_ID)).thenReturn(List.of(
                new Element(21L, LIST_ID, "text", null, null, null, true, null, null)));

        service(0).prepareLaunch(TRAINING_ID, USER_ID, null);

        verify(trainings, never()).countRunning();
    }

    @Test
    void ensurePendingForLaunchRejectsWithoutCreatingARowWhileATrainingRuns() {
        when(lists.findById(LIST_ID)).thenReturn(Optional.of(
                new ItemList(LIST_ID, "list", "desc", false, USER_ID, null, null)));
        when(trainings.existsRunningByListId(LIST_ID)).thenReturn(true);

        assertThrows(ConflictException.class,
                () -> service(5).ensurePendingForLaunch(LIST_ID, USER_ID, null));

        verify(trainings, never()).save(any());
    }
}
