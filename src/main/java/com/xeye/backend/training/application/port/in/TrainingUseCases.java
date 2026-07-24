package com.xeye.backend.training.application.port.in;

import com.xeye.backend.training.domain.model.Training;

import java.util.List;

/** Puerto de entrada: leer el historial de trainings de una lista o un training concreto. */
public interface TrainingUseCases {

    List<Training> listByList(Long userId, Long listId);

    Training get(Long userId, Long trainingId);

    /** Trainings PENDING del usuario en todas sus listas (alimenta los avisos de la UI). */
    List<Training> pendingForUser(Long userId);
}
