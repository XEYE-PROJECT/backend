package com.xeye.backend.training.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.List;

/** Vincula {@code xeye.training.*}. */
@ConfigurationProperties(prefix = "xeye.training")
public record TrainingProperties(
        String provider,
        String webhookSecret,
        String callbackBaseUrl,
        long mockDelayMs,
        /** Modelos de embedding elegibles para un training; el primero es el por defecto. Cada
         *  nombre debe poder cargarlo sentence-transformers tanto en el worker como en el
         *  search-service (ambos los hornean en sus imágenes). */
        List<String> embeddingModels,
        /** Un training lanzado sin actualización del webhook en este tiempo se da por estancado
         *  y se marca fallido (su lista vuelve a pending). 0 desactiva el barrido. */
        long stalledAfterMinutes,
        /** Máximo de trainings corriendo a la vez en todo el backend, para no saturar la
         *  máquina que ejecuta los workers; los lanzamientos que lo superarían se rechazan
         *  con 409. {@code <= 0} desactiva el límite. */
        int maxConcurrent,
        Pricing pricing,
        Docker docker,
        RunPod runpod) {

    public String callbackUrl() {
        String base = callbackBaseUrl == null ? "" : callbackBaseUrl.replaceAll("/+$", "");
        return base + "/webhooks/training-update";
    }

    /**
     * Contenedores locales de un solo uso ({@code provider=docker}).
     *
     * @param inputDir     dónde escribe <em>este proceso</em> el JSON del job
     * @param hostInputDir el mismo directorio tal como lo ve el <em>daemon de docker</em> —
     *                     difieren cuando el backend corre en un contenedor, porque un bind
     *                     mount siempre se resuelve en el host
     * @param network      red docker del contenedor, para que pueda llamar a nuestro webhook
     * @param env          variables {@code KEY=VALUE} extra para el worker (p. ej. {@code ENRICHER=groq})
     * @param gpus         valor de {@code docker run --gpus} ("all", "device=0", …); vacío = CPU.
     *                     El launcher reintenta sin GPU si el daemon no puede darla, así que
     *                     dejar "all" es seguro en una máquina sin GPU
     */
    public record Docker(String image, String inputDir, String hostInputDir, String network,
                         List<String> env, String gpus, String dockerBinary) {
    }

    public record RunPod(String apiKey, String endpointId, int timeoutSeconds) {
    }

    /**
     * Precio preestablecido de un entrenamiento (única fuente de precios del sistema): un fijo
     * por entrenamiento más un precio por cada descripción LLM a generar (elementos sin caché).
     * Se estima con él antes de lanzar y se fija en la fila del training al lanzar.
     *
     * @param fixed          EUR por entrenamiento, se generen o no descripciones
     * @param perDescription EUR por descripción a generar (derivado de la tarifa real del LLM)
     */
    public record Pricing(double fixed, double perDescription) {
    }
}
