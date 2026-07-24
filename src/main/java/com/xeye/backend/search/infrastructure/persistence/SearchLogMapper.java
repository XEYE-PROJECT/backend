package com.xeye.backend.search.infrastructure.persistence;

import com.xeye.backend.search.domain.model.SearchLog;

final class SearchLogMapper {

    private SearchLogMapper() {
    }

    static SearchLog toDomain(SearchLogJpaEntity entity) {
        return new SearchLog(
                entity.getId(),
                entity.getUserId(),
                entity.getApiKeyId(),
                entity.getListId(),
                entity.getListName(),
                entity.getEndpoint(),
                entity.getSearchTerm(),
                entity.getTotalResults(),
                entity.getDurationMs(),
                entity.getSession(),
                entity.getResults(),
                entity.getSearchedAt(),
                entity.getCreatedAt());
    }

    static SearchLogJpaEntity toEntity(SearchLog log) {
        SearchLogJpaEntity entity = new SearchLogJpaEntity();
        entity.setId(log.id());
        entity.setUserId(log.userId());
        entity.setApiKeyId(log.apiKeyId());
        entity.setListId(log.listId());
        entity.setListName(log.listName());
        entity.setEndpoint(log.endpoint());
        entity.setSearchTerm(log.searchTerm());
        entity.setTotalResults(log.totalResults());
        entity.setDurationMs(log.durationMs());
        entity.setSession(log.session());
        entity.setResults(log.results());
        entity.setSearchedAt(log.searchedAt());
        return entity;
    }
}
