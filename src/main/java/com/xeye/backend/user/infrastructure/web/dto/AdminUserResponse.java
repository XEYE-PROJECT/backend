package com.xeye.backend.user.infrastructure.web.dto;

import com.xeye.backend.user.domain.model.User;

import java.time.Instant;

public record AdminUserResponse(
        Long id,
        String name,
        String surname,
        String email,
        String permission,
        boolean emailVerified,
        boolean mfaEnabled,
        String ssoProvider,
        int failedLoginCount,
        Instant lockedUntil,
        Instant lastLoginAt,
        Instant createdAt) {

    public static AdminUserResponse from(User user) {
        return new AdminUserResponse(user.id(), user.name(), user.surname(), user.email(), user.permission().value(),
                user.emailVerified(), user.totpEnabled(), user.ssoProvider(), user.failedLoginCount(),
                user.lockedUntil(), user.lastLoginAt(), user.createdAt());
    }
}
