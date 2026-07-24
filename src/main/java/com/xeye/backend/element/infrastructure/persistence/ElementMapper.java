package com.xeye.backend.element.infrastructure.persistence;

import com.xeye.backend.element.domain.model.Element;

final class ElementMapper {

    private ElementMapper() {
    }

    static Element toDomain(ElementJpaEntity entity) {
        return new Element(
                entity.getId(),
                entity.getListId(),
                entity.getText(),
                entity.getParams(),
                entity.getDescription(),
                entity.getGeneratedDescription(),
                entity.isTrained(),
                entity.getCreatedAt(),
                entity.getUpdatedAt());
    }

    static ElementJpaEntity toEntity(Element element) {
        ElementJpaEntity entity = new ElementJpaEntity();
        entity.setId(element.id());
        entity.setListId(element.listId());
        entity.setText(element.text());
        entity.setParams(element.params());
        entity.setDescription(element.description());
        entity.setGeneratedDescription(element.generatedDescription());
        entity.setTrained(element.trained());
        return entity;
    }
}
