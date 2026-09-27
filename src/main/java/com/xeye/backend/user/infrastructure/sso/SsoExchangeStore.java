package com.xeye.backend.user.infrastructure.sso;

import com.xeye.backend.user.application.command.LoginOutcome;
import org.springframework.stereotype.Component;

import java.security.SecureRandom;
import java.time.Instant;
import java.util.Base64;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * El callback del SSO acaba con una redirección al frontend; para no poner el JWT en la URL, se
 * entrega un código de un solo uso (60 s) que la SPA canjea con {@code POST /auth/sso/exchange}.
 */
@Component
public class SsoExchangeStore {

    private static final long TTL_SECONDS = 60;
    private static final SecureRandom RANDOM = new SecureRandom();

    private final Map<String, Entry> entries = new ConcurrentHashMap<>();

    public String put(LoginOutcome outcome) {
        purge();
        byte[] bytes = new byte[32];
        RANDOM.nextBytes(bytes);
        String code = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
        entries.put(code, new Entry(outcome, Instant.now().plusSeconds(TTL_SECONDS)));
        return code;
    }

    public Optional<LoginOutcome> take(String code) {
        if (code == null) {
            return Optional.empty();
        }
        Entry entry = entries.remove(code);
        return entry == null || entry.expiresAt.isBefore(Instant.now()) ? Optional.empty() : Optional.of(entry.outcome);
    }

    private void purge() {
        Instant now = Instant.now();
        entries.entrySet().removeIf(e -> e.getValue().expiresAt.isBefore(now));
    }

    private record Entry(LoginOutcome outcome, Instant expiresAt) {
    }
}
