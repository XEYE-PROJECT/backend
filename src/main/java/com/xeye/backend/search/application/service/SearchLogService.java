package com.xeye.backend.search.application.service;

import com.xeye.backend.list.application.port.in.ListQueryPort;
import com.xeye.backend.search.application.command.RecordSearchCommand;
import com.xeye.backend.search.application.port.in.SearchLogUseCases;
import com.xeye.backend.search.application.port.out.SearchLogRepository;
import com.xeye.backend.search.domain.model.SearchLog;
import com.xeye.backend.shared.exception.NotFoundException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;

@Service
public class SearchLogService implements SearchLogUseCases {

    private static final Logger log = LoggerFactory.getLogger(SearchLogService.class);
    private static final int MAX_PAGE = 200;

    private final SearchLogRepository searchLogs;
    private final ListQueryPort lists;

    public SearchLogService(SearchLogRepository searchLogs, ListQueryPort lists) {
        this.searchLogs = searchLogs;
        this.lists = lists;
    }

    /**
     * A propósito NO es una sola transacción: la ingesta es asíncrona, y una entrada cuya
     * lista/api-key/usuario ya se borró (violación de FK) no debe envenenar el lote —
     * cada entrada se guarda por separado y los fallos se omiten.
     */
    @Override
    public int recordAll(List<RecordSearchCommand> commands) {
        int accepted = 0;
        for (RecordSearchCommand command : commands) {
            if (!isValid(command)) {
                continue;
            }
            try {
                searchLogs.save(toLog(command));
                accepted++;
            } catch (Exception ex) {
                log.warn("Skipped search-log entry (list {}): {}", command.listId(), ex.getMessage());
            }
        }
        if (accepted < commands.size()) {
            log.warn("Accepted {}/{} search-log entries", accepted, commands.size());
        }
        return accepted;
    }

    @Override
    @Transactional(readOnly = true)
    public List<SearchLog> listByList(Long userId, Long listId, int limit) {
        lists.findById(listId)
                .filter(list -> list.userId().equals(userId))
                .orElseThrow(() -> new NotFoundException("List not found"));
        return searchLogs.findByListId(listId, Math.max(1, Math.min(limit, MAX_PAGE)));
    }

    private boolean isValid(RecordSearchCommand command) {
        return command.userId() != null
                && command.listName() != null && !command.listName().isBlank()
                && command.endpoint() != null && !command.endpoint().isBlank()
                && command.searchTerm() != null;
    }

    private SearchLog toLog(RecordSearchCommand command) {
        return SearchLog.create(
                command.userId(), command.apiKeyId(), command.listId(), command.listName(),
                command.endpoint(), command.searchTerm(),
                command.totalResults() == null ? 0 : command.totalResults(),
                command.durationMs() == null ? 0 : command.durationMs(),
                command.session(), command.results(),
                command.searchedAt() == null ? Instant.now() : command.searchedAt());
    }
}
