package com.xeye.backend.list.infrastructure.persistence;

import com.xeye.backend.list.domain.model.ItemList;

final class ListMapper {

    private ListMapper() {
    }

    static ItemList toDomain(ListJpaEntity entity) {
        return new ItemList(
                entity.getId(),
                entity.getName(),
                entity.getDescription(),
                entity.isPublic(),
                entity.isLlmEnrichment(),
                entity.getUserId(),
                entity.getVersion(),
                entity.getCreatedAt(),
                entity.getUpdatedAt());
    }

    static ListJpaEntity toEntity(ItemList list) {
        ListJpaEntity entity = new ListJpaEntity();
        entity.setId(list.id());
        entity.setName(list.name());
        entity.setDescription(list.description());
        entity.setPublic(list.isPublic());
        entity.setLlmEnrichment(list.llmEnrichment());
        entity.setUserId(list.userId());
        // Una fila existente siempre viaja con su versión; sin ella el merge fallaría como
        // inserción duplicada, así que se asume 0 y el bloqueo optimista decide (409 si es vieja).
        entity.setVersion(list.id() == null ? null : list.version() == null ? 0L : list.version());
        return entity;
    }
}
