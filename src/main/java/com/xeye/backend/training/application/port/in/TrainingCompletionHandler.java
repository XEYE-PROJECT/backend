package com.xeye.backend.training.application.port.in;

import com.xeye.backend.training.application.command.TrainingUpdateCommand;

/**
 * Puerto interno de entrada para los callbacks de progreso/finalización (webhook y provider
 * mock). Idempotente: un callback repetido o fuera de orden se ignora (devuelve false).
 * Al completar también marca los elementos {@code trained} y encola el push a búsqueda.
 */
public interface TrainingCompletionHandler {

    /** @return true si el callback cambió algo; false si se ignoró (duplicado, regresión, run terminado) */
    boolean applyUpdate(TrainingUpdateCommand update);
}
