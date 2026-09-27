package com.xeye.backend.user.domain.model;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PasswordPolicyTest {

    @Test
    void enforcesLengthCommonListAndEmail() {
        assertTrue(PasswordPolicy.validate("short", "j@e.com").isPresent());
        assertTrue(PasswordPolicy.validate("password123", "j@e.com").isPresent());
        assertTrue(PasswordPolicy.validate("x".repeat(73), "j@e.com").isPresent());
        assertTrue(PasswordPolicy.validate("ñ".repeat(37), "j@e.com").isPresent()); // 74 bytes UTF-8
        assertTrue(PasswordPolicy.validate("joanmartorell2024", "joanmartorell@e.com").isPresent());
        assertEquals(java.util.Optional.empty(), PasswordPolicy.validate("correct horse battery", "j@e.com"));
        assertEquals(java.util.Optional.empty(), PasswordPolicy.validate("x".repeat(72), null));
    }
}
