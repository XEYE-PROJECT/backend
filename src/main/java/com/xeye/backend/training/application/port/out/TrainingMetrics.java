package com.xeye.backend.training.application.port.out;

/**
 * Contadores de resultado de los entrenamientos (métricas de operación). La implementación
 * Micrometer vive en infraestructura; el flujo de training solo dice qué pasó.
 */
public interface TrainingMetrics {

    String OUTCOME_COMPLETED = "completed";
    String OUTCOME_FAILED = "failed";
    String OUTCOME_STALLED = "stalled";
    String OUTCOME_LAUNCH_FAILED = "launch_failed";

    /** Un run terminó con el resultado indicado (una de las constantes {@code OUTCOME_*}). */
    void recordOutcome(String outcome);
}
