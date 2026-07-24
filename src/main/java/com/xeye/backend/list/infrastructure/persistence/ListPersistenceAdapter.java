package com.xeye.backend.list.infrastructure.persistence;

import com.xeye.backend.list.application.port.out.ListRepository;
import com.xeye.backend.list.domain.model.ItemList;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Optional;

@Component
public class ListPersistenceAdapter implements ListRepository {

    private final ListJpaRepository jpa;

    public ListPersistenceAdapter(ListJpaRepository jpa) {
        this.jpa = jpa;
    }

    @Override
    public List<ItemList> findByUserId(Long userId) {
        return jpa.findByUserIdOrderByIdAsc(userId).stream().map(ListMapper::toDomain).toList();
    }

    @Override
    public Optional<ItemList> findByIdAndUserId(Long id, Long userId) {
        return jpa.findByIdAndUserId(id, userId).map(ListMapper::toDomain);
    }

    @Override
    public Optional<ItemList> findById(Long id) {
        return jpa.findById(id).map(ListMapper::toDomain);
    }

    @Override
    public List<ItemList> findAll() {
        return jpa.findAll().stream().map(ListMapper::toDomain).toList();
    }

    @Override
    public ItemList save(ItemList list) {
        return ListMapper.toDomain(jpa.save(ListMapper.toEntity(list)));
    }

    @Override
    public void deleteById(Long id) {
        jpa.deleteById(id);
    }
}
