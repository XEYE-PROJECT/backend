package com.xeye.backend.apikey.infrastructure.persistence;

import com.xeye.backend.apikey.domain.model.ApiKey;

final class ApiKeyMapper {

    private ApiKeyMapper() {
    }

    static ApiKey toDomain(ApiKeyJpaEntity entity) {
        return new ApiKey(
                entity.getId(),
                entity.getUserId(),
                entity.getName(),
                entity.getApiKey(),
                entity.getCreatedAt(),
                entity.getUpdatedAt());
    }

    static ApiKeyJpaEntity toEntity(ApiKey apiKey) {
        ApiKeyJpaEntity entity = new ApiKeyJpaEntity();
        entity.setId(apiKey.id());
        entity.setUserId(apiKey.userId());
        entity.setName(apiKey.name());
        entity.setApiKey(apiKey.apiKey());
        return entity;
    }
}
