package com.xeye.backend.training.application.port.in;

import com.xeye.backend.training.domain.model.Training;

import java.util.List;

/** Puerto de entrada: leer el historial de trainings de una lista o un training concreto. */
public interface TrainingUseCases {

    /** Un training del historial con su elegibilidad para pasar a {@code in_use} ya calculada. */
    record ListedTraining(Training training, boolean usable) {
    }

    List<ListedTraining> listByList(Long userId, Long listId);

    Training get(Long userId, Long trainingId);

    /**
     * Activa este training como el modelo en uso de la lista. Solo un training completado cuyos
     * {@code elementIds} coincidan con los elementos actuales de la lista puede activarse.
     */
    Training use(Long userId, Long trainingId);

    /** Trainings PENDING del usuario en todas sus listas (alimenta los avisos de la UI). */
    List<Training> pendingForUser(Long userId);

    /**
     * Precio preestablecido de lanzar un entrenamiento de la lista ahora mismo: el fijo por
     * entrenamiento más el precio por cada descripción LLM que habría que generar (elementos
     * sin enriquecimiento cacheado, todos si {@code regenerateDescriptions}, ninguno si
     * {@code noDescriptions}).
     */
    CostEstimate estimateCost(Long userId, Long listId, boolean regenerateDescriptions,
                              boolean noDescriptions);

    /** Desglose de la estimación; {@code total = fixed + enrichment}. */
    record CostEstimate(int descriptionsToGenerate, double fixed, double enrichment, double total) {
    }
}
