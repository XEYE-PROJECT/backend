package com.xeye.backend.search.application.service;

import com.xeye.backend.list.application.port.in.ListQueryPort;
import com.xeye.backend.search.application.command.RecordSearchCommand;
import com.xeye.backend.search.application.port.in.SearchLogUseCases;
import com.xeye.backend.search.application.port.out.SearchLogRepository;
import com.xeye.backend.search.domain.model.SearchLog;
import com.xeye.backend.shared.config.SearchProperties;
import com.xeye.backend.shared.exception.NotFoundException;
import com.xeye.backend.shared.paging.Page;
import com.xeye.backend.shared.paging.Paging;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.util.List;

@Service
public class SearchLogService implements SearchLogUseCases {

    private static final Logger log = LoggerFactory.getLogger(SearchLogService.class);
    static final int PURGE_BATCH = 1000;
    private static final int MAX_PURGE_BATCHES_PER_RUN = 200;

    private final SearchLogRepository searchLogs;
    private final ListQueryPort lists;
    private final SearchProperties properties;

    public SearchLogService(SearchLogRepository searchLogs, ListQueryPort lists, SearchProperties properties) {
        this.searchLogs = searchLogs;
        this.lists = lists;
        this.properties = properties;
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
    public Page<SearchLog> listByList(Long userId, Long listId, Paging paging) {
        lists.findById(listId)
                .filter(list -> list.userId().equals(userId))
                .orElseThrow(() -> new NotFoundException("List not found"));
        return searchLogs.findByListId(listId, paging);
    }

    /** Por lotes cortos, cada uno en su transacción: nunca un DELETE gigante que bloquee la tabla. */
    @Override
    public int purgeExpired() {
        int days = properties.logRetentionDays();
        if (days <= 0) {
            return 0;
        }
        Instant cutoff = Instant.now().minus(Duration.ofDays(days));
        int total = 0;
        for (int i = 0; i < MAX_PURGE_BATCHES_PER_RUN; i++) {
            int deleted = searchLogs.deleteSearchedBefore(cutoff, PURGE_BATCH);
            total += deleted;
            if (deleted < PURGE_BATCH) {
                break;
            }
        }
        if (total > 0) {
            log.info("Purged {} search-log entries older than {} days", total, days);
        }
        return total;
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
