package com.xeye.backend.training.application.port.in;

import com.xeye.backend.training.application.command.TrainingLaunchCommand;
import com.xeye.backend.training.domain.model.Training;

import java.time.Instant;
import java.util.List;

/**
 * Puerto interno que dirige el flujo de lanzamiento: el listener marca un training PENDING en
 * cada edición, el orquestador lo convierte en un run QUEUED (payload incluido) cuando el
 * usuario pulsa el botón, y el barrido falla los runs cuyo worker enmudeció.
 */
public interface TrainingLaunchService {

    /** Marca la lista como pendiente de reentrenar: crea su training PENDING si no existe ya. */
    void ensurePending(Long listId, Long userId);

    /**
     * El usuario pide reentrenar la lista: devuelve su training PENDING, creándolo si ninguna
     * edición lo marcó. Valida antes todo lo que necesita el lanzamiento (propiedad, elementos,
     * modelo conocido) para que una petición mala no deje una fila pendiente huérfana.
     *
     * @param embeddingModel uno de los modelos configurados, o null para el por defecto
     */
    Training ensurePendingForLaunch(Long listId, Long userId, String embeddingModel);

    /**
     * Convierte el training PENDING del usuario en un run QUEUED: fija las opciones (incluido
     * el modelo de embedding), marca los elementos como no entrenados y construye el payload.
     *
     * @param embeddingModel uno de los modelos configurados, o null para el por defecto
     */
    TrainingLaunchCommand prepareLaunch(Long trainingId, Long userId, String embeddingModel);

    void markLaunched(Long trainingId, String instanceId);

    void markFailed(Long trainingId, String error);

    /**
     * Falla todo training lanzado y sin terminar cuya última actualización supere el corte
     * (worker muerto o webhook perdido) y los devuelve para que se re-marquen sus listas.
     * Un webhook tardío aún gana: las transiciones son laxas, vale la última escritura.
     */
    List<Training> failStalled(Instant cutoff);
}
