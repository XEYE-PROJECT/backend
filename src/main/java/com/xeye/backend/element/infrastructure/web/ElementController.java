package com.xeye.backend.element.infrastructure.web;

import com.xeye.backend.element.application.command.CreateElementCommand;
import com.xeye.backend.element.application.command.UpdateElementCommand;
import com.xeye.backend.element.application.port.in.ElementUseCases;
import com.xeye.backend.element.infrastructure.web.dto.CreateElementRequest;
import com.xeye.backend.element.infrastructure.web.dto.ElementResponse;
import com.xeye.backend.element.infrastructure.web.dto.ImportElementsRequest;
import com.xeye.backend.element.infrastructure.web.dto.UpdateElementRequest;
import com.xeye.backend.shared.security.AuthenticatedUser;
import jakarta.validation.Valid;
import tools.jackson.databind.ObjectMapper;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/** Los elementos se listan/crean bajo su lista ({@code /lists/{listId}/elements}) y se actualizan/borran por su propio id ({@code /elements/{id}}). */
@RestController
public class ElementController {

    private final ElementUseCases elements;
    private final ObjectMapper json;

    public ElementController(ElementUseCases elements, ObjectMapper json) {
        this.elements = elements;
        this.json = json;
    }

    @GetMapping("/lists/{listId}/elements")
    public List<ElementResponse> listByList(@AuthenticationPrincipal AuthenticatedUser current,
                                            @PathVariable Long listId) {
        return elements.listByList(current.id(), listId).stream().map(ElementResponse::from).toList();
    }

    @PostMapping("/lists/{listId}/elements")
    @ResponseStatus(HttpStatus.CREATED)
    public ElementResponse create(@AuthenticationPrincipal AuthenticatedUser current,
                                  @PathVariable Long listId,
                                  @Valid @RequestBody CreateElementRequest request) {
        return ElementResponse.from(elements.create(current.id(), listId,
                new CreateElementCommand(request.text(), request.params(), request.description())));
    }

    @PostMapping("/lists/{listId}/elements/import")
    @ResponseStatus(HttpStatus.CREATED)
    public List<ElementResponse> importElements(@AuthenticationPrincipal AuthenticatedUser current,
                                                @PathVariable Long listId,
                                                @Valid @RequestBody ImportElementsRequest request) {
        List<CreateElementCommand> commands = request.elements().stream()
                .map(item -> new CreateElementCommand(item.text(), paramsAsString(item.params()), item.description()))
                .toList();
        return elements.importElements(current.id(), listId, commands).stream()
                .map(ElementResponse::from)
                .toList();
    }

    /** Los params llegan como cualquier valor JSON; las cadenas se guardan tal cual, el resto como texto JSON. */
    private String paramsAsString(Object params) {
        if (params == null) {
            return null;
        }
        if (params instanceof String text) {
            return text.isBlank() ? null : text;
        }
        return json.writeValueAsString(params);
    }

    @PutMapping("/elements/{id}")
    public ElementResponse update(@AuthenticationPrincipal AuthenticatedUser current,
                                  @PathVariable Long id,
                                  @Valid @RequestBody UpdateElementRequest request) {
        return ElementResponse.from(elements.update(current.id(), id,
                new UpdateElementCommand(request.text(), request.params(), request.description())));
    }

    @DeleteMapping("/elements/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@AuthenticationPrincipal AuthenticatedUser current, @PathVariable Long id) {
        elements.delete(current.id(), id);
    }
}
