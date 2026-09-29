package com.xeye.backend.shared.security;

/**
 * Principal de una llamada servidor-a-servidor autenticada por token en cabecera (el worker de
 * training en el webhook, el search-service en la API interna). No hay usuario detrás.
 * {@code trainingId} solo existe en el webhook: es el entrenamiento que firma el token
 * ({@link WebhookTokens}) y el controlador exige que coincida con el del cuerpo.
 */
public record ServicePrincipal(String name, Long trainingId) {

    public ServicePrincipal(String name) {
        this(name, null);
    }
}
