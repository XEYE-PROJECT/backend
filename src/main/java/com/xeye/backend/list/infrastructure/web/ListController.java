package com.xeye.backend.list.infrastructure.web;

import com.xeye.backend.list.application.command.CreateListCommand;
import com.xeye.backend.list.application.command.UpdateListCommand;
import com.xeye.backend.list.application.port.in.ListUseCases;
import com.xeye.backend.list.infrastructure.web.dto.CreateListRequest;
import com.xeye.backend.list.infrastructure.web.dto.ListResponse;
import com.xeye.backend.list.infrastructure.web.dto.UpdateListRequest;
import com.xeye.backend.shared.paging.PageResponse;
import com.xeye.backend.shared.paging.Paging;
import com.xeye.backend.shared.security.AuthenticatedUser;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Size;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@Validated
@RequestMapping("/lists")
public class ListController {

    private final ListUseCases lists;

    public ListController(ListUseCases lists) {
        this.lists = lists;
    }

    /**
     * Listas del usuario, paginadas: {@code ?offset&limit} (máx. 200), {@code ?q=} filtra por
     * nombre/descripción y {@code ?public=true|false} por visibilidad.
     */
    @GetMapping
    public PageResponse<ListResponse> list(@AuthenticationPrincipal AuthenticatedUser current,
                                           @RequestParam(required = false) Integer offset,
                                           @RequestParam(required = false) Integer limit,
                                           @RequestParam(name = "q", required = false) @Size(max = 200) String query,
                                           @RequestParam(name = "public", required = false) Boolean isPublic) {
        return PageResponse.from(lists.listForUser(current.id(), query, isPublic, Paging.of(offset, limit)),
                ListResponse::from);
    }

    @GetMapping("/{id}")
    public ListResponse get(@AuthenticationPrincipal AuthenticatedUser current, @PathVariable Long id) {
        return ListResponse.from(lists.get(current.id(), id));
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public ListResponse create(@AuthenticationPrincipal AuthenticatedUser current,
                               @Valid @RequestBody CreateListRequest request) {
        return ListResponse.from(lists.create(current.id(),
                new CreateListCommand(request.name(), request.description(), request.publicOrDefault())), 0);
    }

    @PutMapping("/{id}")
    public ListResponse update(@AuthenticationPrincipal AuthenticatedUser current,
                               @PathVariable Long id,
                               @Valid @RequestBody UpdateListRequest request) {
        lists.update(current.id(), id,
                new UpdateListCommand(request.name(), request.description(), request.isPublic()));
        return ListResponse.from(lists.get(current.id(), id));
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@AuthenticationPrincipal AuthenticatedUser current, @PathVariable Long id) {
        lists.delete(current.id(), id);
    }
}
