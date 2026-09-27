package com.xeye.backend.shared.outbox;

import java.util.Set;

/**
 * Entrega un tipo de evento del outbox. Cada módulo registra el suyo como bean (el módulo
 * search reenvía al buscador, el módulo training marca pendientes y empuja índices); el
 * relé los descubre por inyección, así {@code shared} no depende de ningún módulo.
 * Lanzar una excepción = reintentar más tarde con backoff.
 */
public interface OutboxHandler {

    Set<String> types();

    void handle(OutboxEvent event) throws Exception;
}
