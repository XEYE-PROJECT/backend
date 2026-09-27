package com.xeye.backend.apikey.infrastructure.persistence;

import com.xeye.backend.apikey.application.port.out.ApiKeyRepository;
import com.xeye.backend.apikey.domain.model.ApiKey;
import com.xeye.backend.shared.paging.Page;
import com.xeye.backend.shared.paging.Paging;
import org.springframework.data.domain.Limit;
import org.springframework.data.domain.PageRequest;
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
    public Page<ApiKey> findByUserId(Long userId, Paging paging) {
        var result = jpa.findByUserIdOrderByIdAsc(userId, PageRequest.of(paging.pageNumber(), paging.limit()));
        return new Page<>(result.getContent().stream().map(ApiKeyMapper::toDomain).toList(),
                result.getTotalElements(), paging.offset(), paging.limit());
    }

    @Override
    public List<ApiKey> findAfterId(long afterId, int limit) {
        return jpa.findByIdGreaterThanOrderByIdAsc(afterId, Limit.of(limit)).stream()
                .map(ApiKeyMapper::toDomain).toList();
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
