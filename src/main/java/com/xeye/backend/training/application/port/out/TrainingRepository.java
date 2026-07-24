package com.xeye.backend.training.application.port.out;

import com.xeye.backend.training.domain.model.Training;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

public interface TrainingRepository {

    List<Training> findByListIdAndUserId(Long listId, Long userId);

    Optional<Training> findByIdAndUserId(Long id, Long userId);

    Optional<Training> findById(Long id);

    /** El único training {@code in_use} de la lista (el modelo activo), si alguno completó. */
    Optional<Training> findInUseByListId(Long listId);

    /** El training PENDING de la lista, si alguna edición ya lo marcó (máximo uno por lista). */
    Optional<Training> findPendingByListId(Long listId);

    /** Todos los trainings PENDING de las listas del usuario (para los avisos de reentrenar). */
    List<Training> findPendingByUserId(Long userId);

    /** Trainings lanzados sin terminar (ver {@code TrainingStatus.isRunning}) sin actualizar desde el corte. */
    List<Training> findRunningUpdatedBefore(Instant cutoff);

    Training save(Training training);

    /** Pone {@code in_use = false} en todos los trainings de la lista (antes de activar uno nuevo). */
    void clearInUseForList(Long listId);
}
