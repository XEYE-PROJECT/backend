package com.xeye.backend.user.infrastructure.security;

import com.xeye.backend.user.application.port.out.SessionRevoker;
import com.xeye.backend.user.application.port.out.UserTokenRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Instant;

/** Purga cada hora los tokens de email caducados (de más de un día) y los JWT revocados ya vencidos. */
@Component
public class AuthMaintenanceSweeper {

    private static final Logger log = LoggerFactory.getLogger(AuthMaintenanceSweeper.class);

    private final UserTokenRepository userTokens;
    private final SessionRevoker sessionRevoker;

    public AuthMaintenanceSweeper(UserTokenRepository userTokens, SessionRevoker sessionRevoker) {
        this.userTokens = userTokens;
        this.sessionRevoker = sessionRevoker;
    }

    @Scheduled(fixedDelayString = "PT1H", initialDelayString = "PT5M")
    public void sweep() {
        Instant now = Instant.now();
        int tokens = userTokens.deleteExpiredBefore(now.minusSeconds(24 * 3600));
        int revoked = sessionRevoker.deleteExpiredBefore(now);
        if (tokens > 0 || revoked > 0) {
            log.info("Auth maintenance: removed {} expired email token(s) and {} expired revoked JWT(s)", tokens, revoked);
        }
    }
}
