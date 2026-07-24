package com.xeye.backend.element.infrastructure.persistence;

import com.xeye.backend.element.application.port.out.ElementRepository;
import com.xeye.backend.element.domain.model.Element;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Optional;

@Component
public class ElementPersistenceAdapter implements ElementRepository {

    private final ElementJpaRepository jpa;

    public ElementPersistenceAdapter(ElementJpaRepository jpa) {
        this.jpa = jpa;
    }

    @Override
    public List<Element> findByListId(Long listId) {
        return jpa.findByListIdOrderByIdAsc(listId).stream().map(ElementMapper::toDomain).toList();
    }

    @Override
    public Optional<Element> findById(Long id) {
        return jpa.findById(id).map(ElementMapper::toDomain);
    }

    @Override
    public Element save(Element element) {
        return ElementMapper.toDomain(jpa.save(ElementMapper.toEntity(element)));
    }

    @Override
    public void deleteById(Long id) {
        jpa.deleteById(id);
    }

    @Override
    public void updateTrainedByListId(Long listId, boolean trained) {
        jpa.updateTrainedByListId(listId, trained);
    }

    @Override
    public void updateGeneratedDescription(Long listId, Long elementId, String generatedDescription) {
        jpa.updateGeneratedDescription(listId, elementId, generatedDescription);
    }
}
