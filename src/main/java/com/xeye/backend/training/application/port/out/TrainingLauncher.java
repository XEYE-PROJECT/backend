package com.xeye.backend.training.application.port.out;

import com.xeye.backend.training.application.command.TrainingLaunchCommand;

/** Puerto de salida que envía un job de training. Devuelve el id de la instancia del worker. */
public interface TrainingLauncher {

    String launch(TrainingLaunchCommand command);

    /**
     * El worker ya leyó el job (llegó su primer callback) o nunca lo leerá (lanzamiento fallido,
     * run estancado): liberar lo que el provider guardara localmente para él, como el fichero
     * del job con los textos de la lista. Idempotente; por defecto no hay nada que liberar.
     */
    default void release(Long trainingId) {
    }
}
