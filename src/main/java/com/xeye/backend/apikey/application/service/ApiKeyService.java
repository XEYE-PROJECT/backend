package com.xeye.backend.apikey.application.service;

import com.xeye.backend.apikey.application.port.in.ApiKeyQueryPort;
import com.xeye.backend.apikey.application.port.in.ApiKeyUseCases;
import com.xeye.backend.apikey.application.port.out.ApiKeyGenerator;
import com.xeye.backend.apikey.application.port.out.ApiKeyRepository;
import com.xeye.backend.apikey.domain.model.ApiKey;
import com.xeye.backend.shared.event.ApiKeyCreatedEvent;
import com.xeye.backend.shared.event.ApiKeyDeletedEvent;
import com.xeye.backend.shared.exception.NotFoundException;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class ApiKeyService implements ApiKeyUseCases, ApiKeyQueryPort {

    private static final String DEFAULT_NAME = "API Key";
    private static final int MAX_GENERATION_ATTEMPTS = 5;

    private final ApiKeyRepository apiKeys;
    private final ApiKeyGenerator generator;
    private final ApplicationEventPublisher events;

    public ApiKeyService(ApiKeyRepository apiKeys, ApiKeyGenerator generator, ApplicationEventPublisher events) {
        this.apiKeys = apiKeys;
        this.generator = generator;
        this.events = events;
    }

    @Override
    @Transactional(readOnly = true)
    public List<ApiKey> listForUser(Long userId) {
        return apiKeys.findByUserId(userId);
    }

    @Override
    @Transactional(readOnly = true)
    public List<ApiKey> findAll() {
        return apiKeys.findAll();
    }

    @Override
    @Transactional
    public ApiKey create(Long userId, String name) {
        String value = uniqueKey();
        ApiKey created = apiKeys.save(ApiKey.create(userId, resolveName(name), value));
        events.publishEvent(new ApiKeyCreatedEvent(created.id(), userId, created.apiKey()));
        return created;
    }

    @Override
    @Transactional
    public ApiKey rename(Long userId, Long apiKeyId, String name) {
        ApiKey apiKey = require(userId, apiKeyId);
        apiKey.rename(resolveName(name));
        return apiKeys.save(apiKey);
    }

    @Override
    @Transactional
    public void delete(Long userId, Long apiKeyId) {
        ApiKey apiKey = require(userId, apiKeyId);
        apiKeys.deleteById(apiKey.id());
        events.publishEvent(new ApiKeyDeletedEvent(apiKey.id(), userId));
    }

    private ApiKey require(Long userId, Long apiKeyId) {
        return apiKeys.findByIdAndUserId(apiKeyId, userId)
                .orElseThrow(() -> new NotFoundException("API key not found"));
    }

    private String resolveName(String name) {
        return (name == null || name.isBlank()) ? DEFAULT_NAME : name.trim();
    }

    private String uniqueKey() {
        for (int attempt = 0; attempt < MAX_GENERATION_ATTEMPTS; attempt++) {
            String value = generator.generate();
            if (!apiKeys.existsByApiKey(value)) {
                return value;
            }
        }
        throw new IllegalStateException("Could not generate a unique API key");
    }
}
