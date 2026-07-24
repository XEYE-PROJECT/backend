package com.xeye.backend.apikey.application.port.in;

import com.xeye.backend.apikey.domain.model.ApiKey;

import java.util.List;

/** Puerto de entrada: CRUD de las API keys de un usuario, siempre acotado al propietario. */
public interface ApiKeyUseCases {

    List<ApiKey> listForUser(Long userId);

    ApiKey create(Long userId, String name);

    ApiKey rename(Long userId, Long apiKeyId, String name);

    void delete(Long userId, Long apiKeyId);
}
