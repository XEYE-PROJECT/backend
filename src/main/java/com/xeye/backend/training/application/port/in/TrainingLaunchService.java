package com.xeye.backend.training.application.port.in;

import com.xeye.backend.training.application.command.TrainingLaunchCommand;
import com.xeye.backend.training.domain.model.Training;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

/**
 * Puerto interno que dirige el flujo de lanzamiento: el outbox marca un training PENDING en
 * cada edición, {@link #enqueue} lo mete en cola cuando el usuario pulsa el botón, el
 * despachador ({@code TrainingDispatcher}) elige con {@link #pickNextQueued} el siguiente que
 * cabe en el cupo y lo convierte en payload con {@link #prepareLaunch}, y el barrido falla los
 * runs cuyo worker enmudeció.
 */
public interface TrainingLaunchService {

    /** Marca la lista como pendiente de reentrenar: crea su training PENDING si no existe ya. */
    void ensurePending(Long listId, Long userId);

    /**
     * El usuario pide reentrenar la lista: devuelve su training PENDING, creándolo si ninguna
     * edición lo marcó. Valida antes todo lo que necesita el lanzamiento (propiedad, elementos,
     * modelo conocido, ningún run en curso) para que una petición mala no deje una fila
     * pendiente huérfana.
     *
     * @param embeddingModel uno de los modelos configurados, o null para el por defecto
     */
    Training ensurePendingForLaunch(Long listId, Long userId, String embeddingModel);

    /**
     * Mete el training PENDING del usuario en la cola (QUEUED) con sus opciones. Rechaza con 409
     * si la lista ya tiene un run en cola o lanzado.
     *
     * @param embeddingModel         uno de los modelos configurados, o null para el por defecto
     * @param regenerateDescriptions true = pedir al worker (opción {@code force_enrich}) que
     *                               ignore el enriquecimiento cacheado y regenere las
     *                               descripciones LLM de todos los elementos
     * @param noDescriptions         true = entrenar sin descripciones IA (opción
     *                               {@code strategy=embeddings_only}, sin paso LLM); gana a
     *                               {@code regenerateDescriptions}
     */
    Training enqueue(Long trainingId, Long userId, String embeddingModel,
                     boolean regenerateDescriptions, boolean noDescriptions);

    /**
     * El siguiente training de la cola que cabe en los cupos (global y por usuario), con
     * equidad: entre los que caben, primero el del usuario con menos runs lanzados y, a igualdad,
     * el más antiguo. Vacío si no hay hueco o no hay cola.
     */
    Optional<Training> pickNextQueued();

    /**
     * Convierte un training QUEUED en el payload del worker: marca los elementos como no
     * entrenados, captura sus ids y fija el precio. Se llama justo antes de entregarlo al provider.
     */
    TrainingLaunchCommand prepareLaunch(Long trainingId);

    void markLaunched(Long trainingId, String instanceId);

    void markFailed(Long trainingId, String error);

    /**
     * Falla todo training lanzado cuyo último latido sea anterior al corte (worker muerto o
     * webhook perdido) y los devuelve para que se re-marquen sus listas. Un {@code completed}
     * tardío de un run estancado aún se aplica.
     */
    List<Training> failStalled(Instant cutoff);

    /** Empuja al buscador el índice de un training completado (embeddings + elementos actuales). */
    void pushToSearch(Long trainingId);

    /** Borra los runs terminados, no en uso, más antiguos que la retención configurada; devuelve cuántos. */
    int purgeExpired();
}
