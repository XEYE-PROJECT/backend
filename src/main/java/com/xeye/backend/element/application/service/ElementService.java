package com.xeye.backend.element.application.service;

import com.xeye.backend.element.application.command.CreateElementCommand;
import com.xeye.backend.element.application.command.UpdateElementCommand;
import com.xeye.backend.element.application.port.in.ElementQueryPort;
import com.xeye.backend.element.application.port.in.ElementUseCases;
import com.xeye.backend.element.application.port.out.ElementRepository;
import com.xeye.backend.element.domain.model.Element;
import com.xeye.backend.list.application.port.in.ListQueryPort;
import com.xeye.backend.list.domain.model.ItemList;
import com.xeye.backend.shared.event.ListElementsChangedEvent;
import com.xeye.backend.shared.event.TrainingRequestedEvent;
import com.xeye.backend.shared.exception.NotFoundException;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;

@Service
public class ElementService implements ElementUseCases, ElementQueryPort {

    private final ElementRepository elements;
    private final ListQueryPort lists;
    private final ApplicationEventPublisher events;

    public ElementService(ElementRepository elements, ListQueryPort lists, ApplicationEventPublisher events) {
        this.elements = elements;
        this.lists = lists;
        this.events = events;
    }

    @Override
    @Transactional(readOnly = true)
    public List<Element> listByList(Long userId, Long listId) {
        requireOwnedList(userId, listId);
        return elements.findByListId(listId);
    }

    @Override
    @Transactional
    public Element create(Long userId, Long listId, CreateElementCommand command) {
        requireOwnedList(userId, listId);
        Element element = elements.save(
                Element.create(listId, command.text(), command.params(), command.description()));
        requestTraining(listId, userId, "element created");
        notifySearchDataChanged(listId, userId);
        return element;
    }

    @Override
    @Transactional
    public List<Element> importElements(Long userId, Long listId, List<CreateElementCommand> commands) {
        requireOwnedList(userId, listId);
        List<Element> created = commands.stream()
                .map(command -> elements.save(
                        Element.create(listId, command.text(), command.params(), command.description())))
                .toList();
        if (!created.isEmpty()) {
            requestTraining(listId, userId, "elements imported");
            notifySearchDataChanged(listId, userId);
        }
        return created;
    }

    @Override
    @Transactional
    public Element update(Long userId, Long elementId, UpdateElementCommand command) {
        Element element = elements.findById(elementId)
                .orElseThrow(() -> new NotFoundException("Element not found"));
        requireOwnedList(userId, element.listId());

        boolean retrainNeeded = false;
        boolean changed = false;
        if (command.text() != null) {
            boolean textChanged = element.changeText(command.text());
            retrainNeeded |= textChanged;
            changed |= textChanged;
        }
        if (command.description() != null) {
            retrainNeeded |= element.changeDescription(command.description());
        }
        if (command.params() != null) {
            // Los params nunca reentrenan, pero la búsqueda los devuelve:
            // hay que avisar igualmente al search-service para que recargue la lista.
            changed |= element.changeParams(command.params());
        }

        Element saved = elements.save(element);
        if (retrainNeeded) {
            requestTraining(element.listId(), userId, "element text/description changed");
        }
        if (changed) {
            notifySearchDataChanged(element.listId(), userId);
        }
        return saved;
    }

    @Override
    @Transactional
    public void delete(Long userId, Long elementId) {
        Element element = elements.findById(elementId)
                .orElseThrow(() -> new NotFoundException("Element not found"));
        requireOwnedList(userId, element.listId());
        elements.deleteById(elementId);
        requestTraining(element.listId(), userId, "element deleted");
        notifySearchDataChanged(element.listId(), userId);
    }

    @Override
    @Transactional(readOnly = true)
    public List<Element> findByListId(Long listId) {
        return elements.findByListId(listId);
    }

    @Override
    @Transactional
    public void markAllTrained(Long listId, boolean trained) {
        elements.updateTrainedByListId(listId, trained);
    }

    @Override
    @Transactional
    public void saveGeneratedDescriptions(Long listId, Map<Long, String> byElementId) {
        // Sin evento de entrenamiento ni cambio de `trained`: el enriquecimiento deriva del
        // elemento, no lo modifica. Los borrados a mitad de entrenamiento no casan con ninguna fila.
        byElementId.forEach((elementId, generated) ->
                elements.updateGeneratedDescription(listId, elementId, generated));
    }

    private ItemList requireOwnedList(Long userId, Long listId) {
        return lists.findById(listId)
                .filter(list -> list.userId().equals(userId))
                .orElseThrow(() -> new NotFoundException("List not found"));
    }

    private void requestTraining(Long listId, Long userId, String reason) {
        events.publishEvent(new TrainingRequestedEvent(listId, userId, reason));
    }

    private void notifySearchDataChanged(Long listId, Long userId) {
        events.publishEvent(new ListElementsChangedEvent(listId, userId));
    }
}
