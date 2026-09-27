package com.xeye.backend.search.infrastructure.web;

import com.xeye.backend.search.application.port.in.SearchLogUseCases;
import com.xeye.backend.search.infrastructure.web.dto.SearchLogResponse;
import com.xeye.backend.shared.paging.PageResponse;
import com.xeye.backend.shared.paging.Paging;
import com.xeye.backend.shared.security.AuthenticatedUser;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** Lectura del historial de búsquedas de una lista, restringida a su propietario. */
@RestController
public class SearchLogController {

    private final SearchLogUseCases searchLogs;

    public SearchLogController(SearchLogUseCases searchLogs) {
        this.searchLogs = searchLogs;
    }

    /** Página ({@code ?offset&limit}, máx. 200) del historial, los más recientes primero. */
    @GetMapping("/lists/{listId}/searches")
    public PageResponse<SearchLogResponse> listByList(@AuthenticationPrincipal AuthenticatedUser current,
                                                      @PathVariable Long listId,
                                                      @RequestParam(required = false) Integer offset,
                                                      @RequestParam(required = false) Integer limit) {
        return PageResponse.from(searchLogs.listByList(current.id(), listId, Paging.of(offset, limit)),
                SearchLogResponse::from);
    }
}
