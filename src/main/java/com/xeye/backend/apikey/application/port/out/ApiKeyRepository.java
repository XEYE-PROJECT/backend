package com.xeye.backend.apikey.application.port.out;

import com.xeye.backend.apikey.domain.model.ApiKey;
import com.xeye.backend.shared.paging.Page;
import com.xeye.backend.shared.paging.Paging;

import java.util.List;
import java.util.Optional;

public interface ApiKeyRepository {

    Page<ApiKey> findByUserId(Long userId, Paging paging);

    /** Paginación por clave para el snapshot del buscador: claves con id > afterId, id ascendente. */
    List<ApiKey> findAfterId(long afterId, int limit);

    Optional<ApiKey> findByIdAndUserId(Long id, Long userId);

    boolean existsByKeyHash(String keyHash);

    ApiKey save(ApiKey apiKey);

    void deleteById(Long id);
}
