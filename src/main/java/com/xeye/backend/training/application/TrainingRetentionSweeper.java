package com.xeye.backend.training.application;

import com.xeye.backend.training.application.port.in.TrainingLaunchService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/** Retención de runs terminados ({@code xeye.training.retention-days}), una vez al día de madrugada. */
@Component
public class TrainingRetentionSweeper {

    private static final Logger log = LoggerFactory.getLogger(TrainingRetentionSweeper.class);

    private final TrainingLaunchService launchService;

    public TrainingRetentionSweeper(TrainingLaunchService launchService) {
        this.launchService = launchService;
    }

    @Scheduled(cron = "${xeye.training.retention-cron:0 40 4 * * *}")
    public void sweep() {
        try {
            launchService.purgeExpired();
        } catch (Exception ex) {
            log.error("Training retention sweep failed", ex);
        }
    }
}
