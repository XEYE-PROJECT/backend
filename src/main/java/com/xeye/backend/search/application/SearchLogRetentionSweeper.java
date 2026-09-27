package com.xeye.backend.search.application;

import com.xeye.backend.search.application.port.in.SearchLogUseCases;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/** Retención del historial de búsquedas ({@code xeye.search.log-retention-days}), una vez al día de madrugada. */
@Component
public class SearchLogRetentionSweeper {

    private static final Logger log = LoggerFactory.getLogger(SearchLogRetentionSweeper.class);

    private final SearchLogUseCases searchLogs;

    public SearchLogRetentionSweeper(SearchLogUseCases searchLogs) {
        this.searchLogs = searchLogs;
    }

    @Scheduled(cron = "${xeye.search.log-retention-cron:0 20 4 * * *}")
    public void sweep() {
        try {
            searchLogs.purgeExpired();
        } catch (Exception ex) {
            log.error("Search-log retention sweep failed", ex);
        }
    }
}
