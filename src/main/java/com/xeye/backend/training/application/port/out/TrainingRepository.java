package com.xeye.backend.training.application.port.out;

import com.xeye.backend.shared.paging.Page;
import com.xeye.backend.shared.paging.Paging;
import com.xeye.backend.training.domain.model.Training;
import com.xeye.backend.training.domain.model.TrainingStatus;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;

public interface TrainingRepository {

    /** Historial de la lista, los más recientes primero. Nunca carga los embeddings. */
    Page<Training> findByListIdAndUserId(Long listId, Long userId, Paging paging);

    Optional<Training> findByIdAndUserId(Long id, Long userId);

    Optional<Training> findById(Long id);

    /** El único training {@code in_use} de la lista (el modelo activo), si alguno completó. */
    Optional<Training> findInUseByListId(Long listId);

    /** El training PENDING de la lista, si alguna edición ya lo marcó (máximo uno por lista). */
    Optional<Training> findPendingByListId(Long listId);

    /** Todos los trainings PENDING de las listas del usuario (para los avisos de reentrenar). */
    List<Training> findPendingByUserId(Long userId);

    /** Runs lanzados ({@code TrainingStatus.isLaunched}) cuyo último latido es anterior al corte. */
    List<Training> findLaunchedWithHeartbeatBefore(Instant cutoff);

    /** ¿Tiene la lista un run en cola o lanzado? (una lista solo admite uno a la vez). */
    boolean existsInProgressByListId(Long listId);

    /** Runs lanzados en todo el backend (ocupan hueco del cupo global). */
    long countLaunched();

    /** Runs en un estado concreto (gauges de la cola: queued, pending). */
    long countByStatus(TrainingStatus status);

    /** Runs lanzados por usuario (para la equidad de la cola). */
    Map<Long, Long> countLaunchedByUser();

    /** La cola: trainings QUEUED, los más antiguos primero. */
    List<Training> findQueued();

    /** Posición 1-based en la cola por antigüedad (cuántos QUEUED tienen id menor + 1). */
    int queuePosition(Long trainingId);

    Training save(Training training);

    /** Pone {@code in_use = false} en todos los trainings de la lista (antes de activar uno nuevo). */
    void clearInUseForList(Long listId);

    /** Embeddings de un run (tabla aparte; solo se cargan para el push al buscador y la API interna). */
    Optional<String> findEmbeddings(Long trainingId);

    void saveEmbeddings(Long trainingId, String embeddingsData);

    /** Borra hasta {@code batchSize} runs terminados, no en uso, más antiguos que el corte; devuelve cuántos. */
    int deleteTerminalNotInUseBefore(Instant cutoff, int batchSize);
}
