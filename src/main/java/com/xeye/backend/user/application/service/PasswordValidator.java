package com.xeye.backend.user.application.service;

import com.xeye.backend.shared.exception.BadRequestException;
import com.xeye.backend.user.application.port.out.BreachedPasswordChecker;
import com.xeye.backend.user.domain.model.PasswordPolicy;
import org.springframework.stereotype.Component;

/** Política de dominio + comprobación de filtraciones. Lanza 400 con código {@code WEAK_PASSWORD} / {@code BREACHED_PASSWORD}. */
@Component
public class PasswordValidator {

    private final BreachedPasswordChecker breached;

    public PasswordValidator(BreachedPasswordChecker breached) {
        this.breached = breached;
    }

    public void require(String rawPassword, String email) {
        PasswordPolicy.validate(rawPassword, email)
                .ifPresent(reason -> {
                    throw new BadRequestException(reason, "WEAK_PASSWORD");
                });
        if (breached.isBreached(rawPassword)) {
            throw new BadRequestException(
                    "This password appears in known data breaches; choose a different one", "BREACHED_PASSWORD");
        }
    }
}
