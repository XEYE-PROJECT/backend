package com.xeye.backend.apikey.application.port.in;

import com.xeye.backend.apikey.domain.model.ApiKey;

import java.util.List;

/**
 * Puerto interno para el módulo search: todas las claves (valores en claro incluidos)
 * para la caché de autenticación del search-service. No se expone a usuarios finales.
 */
public interface ApiKeyQueryPort {

    List<ApiKey> findAll();
}
