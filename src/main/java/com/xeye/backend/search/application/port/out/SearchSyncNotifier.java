package com.xeye.backend.search.application.port.out;

/**
 * Puerto de salida: avisa al microservicio de búsqueda de que cambiaron datos que cachea.
 * Todo es best-effort: si un aviso se pierde, búsqueda recarga perezosamente desde nuestra
 * API interna, así que solo supone una desactualización breve.
 */
public interface SearchSyncNotifier {

    void listMetaChanged(Long listId, Long userId, String name, boolean isPublic);

    void listDeleted(Long listId);

    /** Los elementos de la lista cambiaron; búsqueda debe descartar su copia cacheada y recargar. */
    void listDataInvalidated(Long listId);

    void apiKeyCreated(Long apiKeyId, Long userId, String apiKey);

    void apiKeyDeleted(Long apiKeyId);

    void userDeleted(Long userId);
}
