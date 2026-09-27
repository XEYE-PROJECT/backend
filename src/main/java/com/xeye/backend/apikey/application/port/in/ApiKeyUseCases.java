package com.xeye.backend.apikey.application.port.in;

import com.xeye.backend.apikey.domain.model.ApiKey;
import com.xeye.backend.shared.paging.Page;
import com.xeye.backend.shared.paging.Paging;

/** Puerto de entrada: CRUD de las API keys de un usuario, siempre acotado al propietario. */
public interface ApiKeyUseCases {

    Page<ApiKey> listForUser(Long userId, Paging paging);

    /** Crea la clave y devuelve su valor en claro: la única vez que existe fuera del hash. */
    CreatedApiKey create(Long userId, String name);

    ApiKey rename(Long userId, Long apiKeyId, String name);

    void delete(Long userId, Long apiKeyId);

    record CreatedApiKey(ApiKey apiKey, String rawKey) {
    }
}
