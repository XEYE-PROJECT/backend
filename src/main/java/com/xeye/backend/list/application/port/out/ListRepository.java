package com.xeye.backend.list.application.port.out;

import com.xeye.backend.list.domain.model.ItemList;

import java.util.List;
import java.util.Optional;

public interface ListRepository {

    List<ItemList> findByUserId(Long userId);

    Optional<ItemList> findByIdAndUserId(Long id, Long userId);

    Optional<ItemList> findById(Long id);

    List<ItemList> findAll();

    ItemList save(ItemList list);

    void deleteById(Long id);
}
