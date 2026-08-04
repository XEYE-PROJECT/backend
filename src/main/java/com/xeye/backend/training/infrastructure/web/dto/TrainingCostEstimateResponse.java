package com.xeye.backend.training.infrastructure.web.dto;

import com.xeye.backend.training.application.port.in.TrainingUseCases.CostEstimate;

/**
 * Respuesta de {@code GET /lists/{listId}/trainings/estimate}: el precio preestablecido de
 * lanzar un entrenamiento de la lista ahora mismo ({@code total = fixed + enrichment}).
 */
public record TrainingCostEstimateResponse(int descriptionsToGenerate, double fixed,
                                           double enrichment, double total) {

    public static TrainingCostEstimateResponse from(CostEstimate estimate) {
        return new TrainingCostEstimateResponse(estimate.descriptionsToGenerate(), estimate.fixed(),
                estimate.enrichment(), estimate.total());
    }
}
