package com.xeye.backend.user.infrastructure.persistence;

import com.xeye.backend.user.domain.model.Permission;
import com.xeye.backend.user.domain.model.User;

import java.util.Arrays;
import java.util.List;

/** Convierte entre el dominio {@link User} y su entidad JPA. */
final class UserMapper {

    private UserMapper() {
    }

    static User toDomain(UserJpaEntity entity) {
        List<String> recovery = entity.getRecoveryCodes() == null || entity.getRecoveryCodes().isBlank()
                ? List.of()
                : Arrays.stream(entity.getRecoveryCodes().split(",")).filter(s -> !s.isBlank()).toList();
        return new User(
                entity.getId(),
                entity.getName(),
                entity.getSurname(),
                entity.getEmail(),
                entity.getPassword(),
                Permission.fromString(entity.getPermission()),
                entity.isEmailVerified(),
                entity.getLocale(),
                entity.getSearchRateLimitPerMinute(),
                entity.getTokenVersion(),
                entity.getFailedLoginCount(),
                entity.getLockedUntil(),
                entity.getLastLoginAt(),
                entity.getTotpSecret(),
                entity.isTotpEnabled(),
                recovery,
                entity.getSsoProvider(),
                entity.getSsoSubject(),
                entity.getCreatedAt(),
                entity.getUpdatedAt());
    }

    static UserJpaEntity toEntity(User user) {
        UserJpaEntity entity = new UserJpaEntity();
        entity.setId(user.id());
        entity.setName(user.name());
        entity.setSurname(user.surname());
        entity.setEmail(user.email());
        entity.setPassword(user.password());
        entity.setPermission(user.permission().value());
        entity.setEmailVerified(user.emailVerified());
        entity.setLocale(user.locale());
        entity.setSearchRateLimitPerMinute(user.searchRateLimitPerMinute());
        entity.setTokenVersion(user.tokenVersion());
        entity.setFailedLoginCount(user.failedLoginCount());
        entity.setLockedUntil(user.lockedUntil());
        entity.setLastLoginAt(user.lastLoginAt());
        entity.setTotpSecret(user.totpSecret());
        entity.setTotpEnabled(user.totpEnabled());
        entity.setRecoveryCodes(user.recoveryCodeHashes().isEmpty() ? null : String.join(",", user.recoveryCodeHashes()));
        entity.setSsoProvider(user.ssoProvider());
        entity.setSsoSubject(user.ssoSubject());
        return entity;
    }
}
