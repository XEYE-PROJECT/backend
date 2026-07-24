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
                entity.getUserId(),
                entity.getCreatedAt(),
                entity.getUpdatedAt());
    }

    static ListJpaEntity toEntity(ItemList list) {
        ListJpaEntity entity = new ListJpaEntity();
        entity.setId(list.id());
        entity.setName(list.name());
        entity.setDescription(list.description());
        entity.setPublic(list.isPublic());
        entity.setUserId(list.userId());
        return entity;
    }
}
