package com.xeye.backend.list.application.service;

import com.xeye.backend.list.application.command.CreateListCommand;
import com.xeye.backend.list.application.command.UpdateListCommand;
import com.xeye.backend.list.application.port.in.ListQueryPort;
import com.xeye.backend.list.application.port.in.ListUseCases;
import com.xeye.backend.list.application.port.out.ListRepository;
import com.xeye.backend.list.domain.model.ItemList;
import com.xeye.backend.shared.event.ListDeletedEvent;
import com.xeye.backend.shared.event.ListMetaChangedEvent;
import com.xeye.backend.shared.event.TrainingRequestedEvent;
import com.xeye.backend.shared.exception.NotFoundException;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Objects;
import java.util.Optional;

@Service
public class ListService implements ListUseCases, ListQueryPort {

    private final ListRepository lists;
    private final ApplicationEventPublisher events;

    public ListService(ListRepository lists, ApplicationEventPublisher events) {
        this.lists = lists;
        this.events = events;
    }

    @Override
    @Transactional(readOnly = true)
    public List<ItemList> listForUser(Long userId) {
        return lists.findByUserId(userId);
    }

    @Override
    @Transactional(readOnly = true)
    public ItemList get(Long userId, Long listId) {
        return require(userId, listId);
    }

    @Override
    @Transactional
    public ItemList create(Long userId, CreateListCommand command) {
        ItemList list = ItemList.create(userId, command.name(), command.description(), command.isPublic());
        ItemList saved = lists.save(list);
        // Avisar al search-service ya: si no, una lista pública nueva no es buscable
        // hasta que complete un entrenamiento o refresque su catálogo.
        events.publishEvent(new ListMetaChangedEvent(saved.id(), userId, saved.name(), saved.isPublic()));
        return saved;
    }

    @Override
    @Transactional
    public ItemList update(Long userId, Long listId, UpdateListCommand command) {
        ItemList list = require(userId, listId);
        String previousDescription = list.description();
        String previousName = list.name();
        boolean previousVisibility = list.isPublic();

        if (command.name() != null) {
            list.rename(command.name());
        }
        if (command.isPublic() != null) {
            list.changeVisibility(command.isPublic());
        }
        boolean descriptionChanged = false;
        if (command.description() != null && !Objects.equals(previousDescription, command.description())) {
            list.changeDescription(command.description());
            descriptionChanged = true;
        }

        ItemList saved = lists.save(list);

        // Cambiar la descripción altera el contexto de entrenamiento de todos los elementos:
        // hay que reentrenar la lista entera.
        if (descriptionChanged) {
            events.publishEvent(new TrainingRequestedEvent(saved.id(), userId, "list description changed"));
        }
        // Nombre/visibilidad son metadatos relevantes para búsqueda (lookup por nombre, filtro public).
        if (!Objects.equals(previousName, saved.name()) || previousVisibility != saved.isPublic()) {
            events.publishEvent(new ListMetaChangedEvent(saved.id(), userId, saved.name(), saved.isPublic()));
        }
        return saved;
    }

    @Override
    @Transactional
    public void delete(Long userId, Long listId) {
        ItemList list = require(userId, listId);
        lists.deleteById(list.id());
        events.publishEvent(new ListDeletedEvent(list.id(), userId));
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<ItemList> findById(Long listId) {
        return lists.findById(listId);
    }

    @Override
    @Transactional(readOnly = true)
    public List<ItemList> findAll() {
        return lists.findAll();
    }

    private ItemList require(Long userId, Long listId) {
        return lists.findByIdAndUserId(listId, userId)
                .orElseThrow(() -> new NotFoundException("List not found"));
    }
}
