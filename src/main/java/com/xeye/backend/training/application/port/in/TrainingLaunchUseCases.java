package com.xeye.backend.training.application.port.in;

import com.xeye.backend.training.domain.model.Training;

/**
 * Puerto de entrada: el usuario lanza un training pendiente (eligiendo el modelo de embedding).
 * Lanzar = entrar en la cola; el despachador lo arranca en cuanto hay hueco (a menudo en la
 * misma petición), así que la respuesta puede venir ya {@code queued} o {@code initialized}.
 */
public interface TrainingLaunchUseCases {

    /**
     * Encola el training PENDING del usuario con el modelo dado (null = por defecto) y
     * devuelve el training tal como queda. Con {@code regenerateDescriptions} el worker vuelve
     * a generar el enriquecimiento LLM de todos los elementos, ignorando la caché; con
     * {@code noDescriptions} entrena sin descripciones IA (gana a regenerar).
     */
    Training launch(Long userId, Long trainingId, String embeddingModel,
                    boolean regenerateDescriptions, boolean noDescriptions);

    /**
     * Reentrena la lista ya mismo, la haya marcado una edición o no: reutiliza su training
     * PENDING (o crea uno) y lo encola con el modelo dado (null = por defecto).
     */
    Training retrain(Long userId, Long listId, String embeddingModel,
                     boolean regenerateDescriptions, boolean noDescriptions);
}
