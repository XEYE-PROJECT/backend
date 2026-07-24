package com.xeye.backend.training.application.port.in;

import com.xeye.backend.training.domain.model.Training;

/** Puerto de entrada: el usuario lanza un training pendiente (eligiendo el modelo de embedding). */
public interface TrainingLaunchUseCases {

    /**
     * Lanza el training PENDING del usuario con el modelo dado (null = por defecto) y
     * devuelve el training tal como queda tras el intento.
     */
    Training launch(Long userId, Long trainingId, String embeddingModel);

    /**
     * Reentrena la lista ya mismo, la haya marcado una edición o no: reutiliza su training
     * PENDING (o crea uno) y lo lanza con el modelo dado (null = por defecto).
     */
    Training retrain(Long userId, Long listId, String embeddingModel);
}
