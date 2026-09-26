package com.xeye.backend.apikey.infrastructure.persistence;

import com.xeye.backend.apikey.application.port.out.ApiKeyRepository;
import com.xeye.backend.apikey.domain.model.ApiKey;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Optional;

@Component
public class ApiKeyPersistenceAdapter implements ApiKeyRepository {

    private final ApiKeyJpaRepository jpa;

    public ApiKeyPersistenceAdapter(ApiKeyJpaRepository jpa) {
        this.jpa = jpa;
    }

    @Override
    public List<ApiKey> findByUserId(Long userId) {
        return jpa.findByUserIdOrderByIdAsc(userId).stream().map(ApiKeyMapper::toDomain).toList();
    }

    @Override
    public List<ApiKey> findAll() {
        return jpa.findAll().stream().map(ApiKeyMapper::toDomain).toList();
    }

    @Override
    public Optional<ApiKey> findByIdAndUserId(Long id, Long userId) {
        return jpa.findByIdAndUserId(id, userId).map(ApiKeyMapper::toDomain);
    }

    @Override
    public boolean existsByKeyHash(String keyHash) {
        return jpa.existsByKeyHash(keyHash);
    }

    @Override
    public ApiKey save(ApiKey apiKey) {
        return ApiKeyMapper.toDomain(jpa.save(ApiKeyMapper.toEntity(apiKey)));
    }

    @Override
    public void deleteById(Long id) {
        jpa.deleteById(id);
    }
}
