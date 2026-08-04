package com.xeye.backend.training.domain.model;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class TrainingStatusTest {

    @Test
    void valueIsLowercase() {
        assertEquals("completed", TrainingStatus.COMPLETED.value());
        assertEquals("queued", TrainingStatus.QUEUED.value());
    }

    @Test
    void fromStringIsCaseInsensitive() {
        assertEquals(TrainingStatus.OPTIMIZING, TrainingStatus.fromString("Optimizing"));
        assertEquals(TrainingStatus.FAILED, TrainingStatus.fromString(" failed "));
    }

    @Test
    void unknownStatusIsRejected() {
        assertThrows(IllegalArgumentException.class, () -> TrainingStatus.fromString("bogus"));
    }

    @Test
    void completingMarksInUse() {
        Training training = Training.pending(1L, 1L);
        training.markQueued(null);
        training.markCompleted("emb", "model", null, null, null, null);
        assertEquals(TrainingStatus.COMPLETED, training.status());
        assertEquals(true, training.inUse());
    }

    @Test
    void pendingStartsUnlaunched() {
        Training training = Training.pending(1L, 1L);
        assertEquals(TrainingStatus.PENDING, training.status());
        assertEquals("pending", training.status().value());
    }
}
