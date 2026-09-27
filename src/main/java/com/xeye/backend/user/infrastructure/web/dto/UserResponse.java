package com.xeye.backend.user.infrastructure.web.dto;

import com.xeye.backend.user.domain.model.User;

import java.time.Instant;

public record UserResponse(
        Long id,
        String name,
        String surname,
        String email,
        String permission,
        boolean emailVerified,
        String locale,
        boolean mfaEnabled,
        String ssoProvider,
        Instant lastLoginAt,
        Instant createdAt,
        Instant updatedAt) {

    public static UserResponse from(User user) {
        return new UserResponse(
                user.id(),
                user.name(),
                user.surname(),
                user.email(),
                user.permission().value(),
                user.emailVerified(),
                user.locale(),
                user.totpEnabled(),
                user.ssoProvider(),
                user.lastLoginAt(),
                user.createdAt(),
                user.updatedAt());
    }
}
