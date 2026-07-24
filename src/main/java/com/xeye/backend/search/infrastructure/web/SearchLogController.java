package com.xeye.backend.search.infrastructure.web;

import com.xeye.backend.search.application.port.in.SearchLogUseCases;
import com.xeye.backend.search.infrastructure.web.dto.SearchLogResponse;
import com.xeye.backend.shared.security.AuthenticatedUser;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/** Lectura del historial de búsquedas de una lista, restringida a su propietario. */
@RestController
public class SearchLogController {

    private final SearchLogUseCases searchLogs;

    public SearchLogController(SearchLogUseCases searchLogs) {
        this.searchLogs = searchLogs;
    }

    @GetMapping("/lists/{listId}/searches")
    public List<SearchLogResponse> listByList(@AuthenticationPrincipal AuthenticatedUser current,
                                              @PathVariable Long listId,
                                              @RequestParam(defaultValue = "50") int limit) {
        return searchLogs.listByList(current.id(), listId, limit).stream()
                .map(SearchLogResponse::from)
                .toList();
    }
}
