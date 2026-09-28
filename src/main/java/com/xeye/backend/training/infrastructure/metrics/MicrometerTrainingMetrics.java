package com.xeye.backend.training.infrastructure.metrics;

import com.xeye.backend.training.application.port.out.TrainingMetrics;
import com.xeye.backend.training.application.port.out.TrainingRepository;
import com.xeye.backend.training.domain.model.TrainingStatus;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.function.ToDoubleFunction;

/**
 * Métricas Prometheus del flujo de training ({@code GET /actuator/prometheus}, solo por la red
 * docker): contador {@code xeye_trainings_total{outcome}} (completed / failed / stalled /
 * launch_failed) y gauges del estado de la cola ({@code xeye_trainings_queued},
 * {@code xeye_trainings_launched}, {@code xeye_trainings_pending}) leídos de la BD en cada
 * scrape. Las alertas de "trainings fallidos" del stack de monitorización se basan en el contador.
 */
@Component
public class MicrometerTrainingMetrics implements TrainingMetrics {

    private static final Logger log = LoggerFactory.getLogger(MicrometerTrainingMetrics.class);

    private final MeterRegistry registry;

    public MicrometerTrainingMetrics(MeterRegistry registry, TrainingRepository trainings) {
        this.registry = registry;
        gauge("xeye.trainings.queued", "Trainings waiting in the queue", trainings,
                r -> r.countByStatus(TrainingStatus.QUEUED));
        gauge("xeye.trainings.pending", "Lists with edits waiting for a retrain", trainings,
                r -> r.countByStatus(TrainingStatus.PENDING));
        gauge("xeye.trainings.launched", "Trainings running on a worker", trainings, TrainingRepository::countLaunched);
        // Se registran de antemano para que la serie exista (y valga 0) antes del primer evento.
        for (String outcome : List.of(OUTCOME_COMPLETED, OUTCOME_FAILED, OUTCOME_STALLED, OUTCOME_LAUNCH_FAILED)) {
            counter(outcome);
        }
    }

    @Override
    public void recordOutcome(String outcome) {
        counter(outcome).increment();
    }

    private Counter counter(String outcome) {
        return Counter.builder("xeye.trainings")
                .description("Trainings finished, by outcome")
                .tag("outcome", outcome)
                .register(registry);
    }

    private void gauge(String name, String description, TrainingRepository trainings,
                       ToDoubleFunction<TrainingRepository> reader) {
        Gauge.builder(name, trainings, r -> {
                    try {
                        return reader.applyAsDouble(r);
                    } catch (RuntimeException ex) {
                        // Un scrape nunca debe fallar por la BD: la serie queda NaN y la alerta de BD ya avisa.
                        log.debug("Could not read gauge {}: {}", name, ex.getMessage());
                        return Double.NaN;
                    }
                })
                .description(description)
                .register(registry);
    }
}
