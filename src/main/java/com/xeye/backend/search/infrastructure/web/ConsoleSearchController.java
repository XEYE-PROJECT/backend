package com.xeye.backend.search.infrastructure.web;

import com.xeye.backend.search.application.command.ConsoleSearchCommand;
import com.xeye.backend.search.application.port.in.ConsoleSearchUseCases;
import com.xeye.backend.search.infrastructure.web.dto.ConsoleSearchRequest;
import com.xeye.backend.search.infrastructure.web.dto.ConsoleSearchResponse;
import com.xeye.backend.shared.security.AuthenticatedUser;
import jakarta.validation.Valid;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

/**
 * Playground de la consola: {@code POST /lists/{listId}/search} con la sesión del usuario. La API
 * key nunca pasa por el navegador; el backend reenvía la búsqueda al buscador por la red interna.
 */
@RestController
public class ConsoleSearchController {

    private final ConsoleSearchUseCases search;

    public ConsoleSearchController(ConsoleSearchUseCases search) {
        this.search = search;
    }

    @PostMapping("/lists/{listId}/search")
    public ConsoleSearchResponse search(@AuthenticationPrincipal AuthenticatedUser current,
                                        @PathVariable Long listId,
                                        @Valid @RequestBody ConsoleSearchRequest request) {
        return ConsoleSearchResponse.from(search.search(current.id(), listId,
                new ConsoleSearchCommand(request.searchTerm().trim(), request.limitOrDefault(),
                        request.includeScoreBreakdownOrDefault())));
    }
}
