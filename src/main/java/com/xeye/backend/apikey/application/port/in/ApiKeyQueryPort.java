package com.xeye.backend.apikey.application.port.in;

import com.xeye.backend.apikey.domain.model.ApiKey;

import java.util.List;

/**
 * Puerto interno para el módulo search: todas las claves (solo su hash) para la caché de
 * autenticación del search-service, por páginas de clave (id > afterId). No se expone a usuarios.
 */
public interface ApiKeyQueryPort {

    List<ApiKey> findAfterId(long afterId, int limit);
}
