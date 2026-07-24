package com.xeye.backend.user.domain.model;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class UserTest {

    @Test
    void emailIsNormalizedToLowercase() {
        User user = User.register("Joan", "Martorell", "  Joan@Example.COM ", "hash");
        assertEquals("joan@example.com", user.email());
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
}
