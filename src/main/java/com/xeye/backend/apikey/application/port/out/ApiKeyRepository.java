package com.xeye.backend.apikey.application.port.out;

import com.xeye.backend.apikey.domain.model.ApiKey;

import java.util.List;
import java.util.Optional;

public interface ApiKeyRepository {

    List<ApiKey> findByUserId(Long userId);

    List<ApiKey> findAll();

    Optional<ApiKey> findByIdAndUserId(Long id, Long userId);

    boolean existsByApiKey(String apiKey);

    ApiKey save(ApiKey apiKey);

    void deleteById(Long id);
}
