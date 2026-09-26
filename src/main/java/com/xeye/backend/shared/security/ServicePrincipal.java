package com.xeye.backend.shared.security;

/**
 * Principal de una llamada servidor-a-servidor autenticada por secreto compartido (el worker
 * de training en el webhook, el search-service en la API interna). No hay usuario detrás.
 */
public record ServicePrincipal(String name) {
}
