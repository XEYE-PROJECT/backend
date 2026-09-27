package com.xeye.backend.user.infrastructure.security;

import com.xeye.backend.user.application.port.out.BreachedPasswordChecker;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/** {@code xeye.auth.password.breach-check=none}: sin comprobación externa (dev sin red / tests). */
@Component
@ConditionalOnProperty(name = "xeye.auth.password.breach-check", havingValue = "none")
public class NoopBreachedPasswordChecker implements BreachedPasswordChecker {

    @Override
    public boolean isBreached(String rawPassword) {
        return false;
    }
}
