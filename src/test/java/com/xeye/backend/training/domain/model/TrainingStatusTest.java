package com.xeye.backend.training.domain.model;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Máquina de estados de un training: solo hacia delante, latidos idempotentes, terminales inmutables. */
class TrainingStatusTest {

    @Test
    void parsesCaseInsensitively() {
        assertEquals(TrainingStatus.COMPLETED, TrainingStatus.fromString("Completed"));
        assertEquals(TrainingStatus.PENDING, TrainingStatus.fromString(" pending "));
    }

    @Test
    void unknownStatusIsRejected() {
        assertThrows(IllegalArgumentException.class, () -> TrainingStatus.fromString("bogus"));
    }

    @Test
    void queuedWaitsAndLaunchedCountsTowardsTheCap() {
        assertTrue(TrainingStatus.QUEUED.isInProgress());
        assertFalse(TrainingStatus.QUEUED.isLaunched());
        assertTrue(TrainingStatus.OPTIMIZING.isLaunched());
        assertTrue(TrainingStatus.COMPLETED.isTerminal());
        assertFalse(TrainingStatus.PENDING.isInProgress());
    }

    @Test
    void completingMarksInUseAndRecordsTheHeartbeat() {
        Training training = Training.pending(1L, 1L);
        training.markQueued(null);
        training.markLaunched("job-1");
        training.markCompleted(true, "model", null, null, null, null);
        assertEquals(TrainingStatus.COMPLETED, training.status());
        assertTrue(training.inUse());
        assertTrue(training.hasEmbeddings());
        assertNotNull(training.lastHeartbeatAt());
    }

    @Test
    void progressOnlyMovesForwardAndRepeatedStatesAreHeartbeats() {
        Training training = Training.pending(1L, 1L);
        training.markQueued(List.of());
        training.markLaunched("job-1");
        assertTrue(training.applyProgress(TrainingStatus.OPTIMIZING));
        assertTrue(training.applyProgress(TrainingStatus.OPTIMIZING), "repeated status = heartbeat");
        assertTrue(training.applyProgress(TrainingStatus.TRAINING));
        assertFalse(training.applyProgress(TrainingStatus.OPTIMIZING), "no going back");
        assertEquals(TrainingStatus.TRAINING, training.status());
    }

    @Test
    void terminalRunsIgnoreLateCallbacks() {
        Training training = Training.pending(1L, 1L);
        training.markQueued(List.of());
        training.markLaunched("job-1");
        training.markCompleted(true, "model", null, null, null, null);
        assertFalse(training.applyProgress(TrainingStatus.TRAINING));
        assertFalse(training.canComplete());
        assertFalse(training.canFail());
    }

    @Test
    void aStalledRunMayStillCompleteLate() {
        Training training = Training.pending(1L, 1L);
        training.markQueued(List.of());
        training.markLaunched("job-1");
        training.markStalled();
        assertEquals(TrainingStatus.FAILED, training.status());
        assertTrue(training.wasStalled());
        assertTrue(training.canComplete());
        training.markCompleted(true, "model", null, null, null, null);
        assertEquals(TrainingStatus.COMPLETED, training.status());
        assertNull(training.error());
    }

    @Test
    void anEarlyCallbackIsNotUndoneByMarkLaunched() {
        Training training = Training.pending(1L, 1L);
        training.markQueued(List.of());
        // El worker llamó antes de que el backend anotase el id de instancia.
        assertTrue(training.applyProgress(TrainingStatus.OPTIMIZING));
        training.markLaunched("job-1");
        assertEquals(TrainingStatus.OPTIMIZING, training.status());
        assertEquals("job-1", training.instanceId());
    }

    @Test
    void onlyAPendingTrainingCanBeQueued() {
        Training training = Training.pending(1L, 1L);
        training.markQueued(List.of());
        assertThrows(IllegalStateException.class, () -> training.markQueued(List.of()));
    }

    @Test
    void pendingStartsUnlaunched() {
        Training training = Training.pending(1L, 1L);
        assertEquals(TrainingStatus.PENDING, training.status());
        assertEquals("pending", training.status().value());
    }
}
