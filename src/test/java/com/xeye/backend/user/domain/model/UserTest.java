package com.xeye.backend.user.domain.model;

import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class UserTest {

    @Test
    void emailIsNormalizedToLowercase() {
        User user = User.register("Joan", "Martorell", "  Joan@Example.COM ", "hash");
        assertEquals("joan@example.com", user.email());
        assertFalse(user.emailVerified());
        assertEquals("es", user.locale());
    }

    @Test
    void newUserHasUserPermission() {
        User user = User.register("Joan", "Martorell", "j@e.com", "hash");
        assertEquals(Permission.USER, user.permission());
    }

    @Test
    void blankNameIsRejected() {
        assertThrows(IllegalArgumentException.class,
                () -> User.register("", "Martorell", "j@e.com", "hash"));
    }

    @Test
    void permissionFromStringDefaultsToUser() {
        assertEquals(Permission.USER, Permission.fromString(null));
        assertEquals(Permission.ADMIN, Permission.fromString("ADMIN"));
    }

    @Test
    void failedLoginsLockProgressively() {
        User user = User.register("Joan", "M", "j@e.com", "hash");
        Instant now = Instant.parse("2026-09-27T10:00:00Z");
        for (int i = 1; i < User.LOCK_THRESHOLD; i++) {
            assertEquals(0, user.recordFailedLogin(now));
            assertFalse(user.isLocked(now));
        }
        assertEquals(1, user.recordFailedLogin(now));       // 5º fallo: 1 min
        assertTrue(user.isLocked(now));
        assertFalse(user.isLocked(now.plusSeconds(61)));
        assertEquals(2, user.recordFailedLogin(now));       // 6º: 2 min
        assertEquals(4, user.recordFailedLogin(now));       // 7º: 4 min
        for (int i = 0; i < 10; i++) {
            user.recordFailedLogin(now);
        }
        assertEquals(User.MAX_LOCK_MINUTES, user.recordFailedLogin(now)); // tope
        user.recordSuccessfulLogin(now);
        assertEquals(0, user.failedLoginCount());
        assertFalse(user.isLocked(now));
    }

    @Test
    void credentialChangesBumpTokenVersion() {
        User user = User.register("Joan", "M", "j@e.com", "hash");
        assertEquals(0, user.tokenVersion());
        user.changePassword("hash2");
        assertEquals(1, user.tokenVersion());
        user.changeEmail("new@e.com");
        assertEquals(2, user.tokenVersion());
        assertTrue(user.emailVerified());
        user.invalidateSessions();
        assertEquals(3, user.tokenVersion());
    }

    @Test
    void totpLifecycle() {
        User user = User.register("Joan", "M", "j@e.com", "hash");
        assertThrows(IllegalStateException.class, () -> user.enableTotp(List.of("h1")));
        user.stageTotpSecret("SECRET");
        assertFalse(user.totpEnabled());
        user.enableTotp(List.of("h1", "h2"));
        assertTrue(user.totpEnabled());
        assertThrows(IllegalStateException.class, () -> user.stageTotpSecret("OTHER"));
        assertTrue(user.consumeRecoveryCode("h1"));
        assertFalse(user.consumeRecoveryCode("h1"));
        assertEquals(List.of("h2"), user.recoveryCodeHashes());
        user.disableTotp();
        assertFalse(user.totpEnabled());
        assertEquals(List.of(), user.recoveryCodeHashes());
    }
}
