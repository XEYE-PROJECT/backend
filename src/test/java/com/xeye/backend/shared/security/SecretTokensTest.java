package com.xeye.backend.shared.security;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SecretTokensTest {

    @Test
    void equalSecretsMatch() {
        assertTrue(SecretTokens.constantTimeEquals("s3cr3t-value-0123456789", "s3cr3t-value-0123456789"));
    }

    @Test
    void differentSecretsDoNotMatchWhateverTheirLength() {
        assertFalse(SecretTokens.constantTimeEquals("s3cr3t-value-0123456789", "s3cr3t-value-0123456788"));
        assertFalse(SecretTokens.constantTimeEquals("s3cr3t-value-0123456789", "s3cr3t"));
        assertFalse(SecretTokens.constantTimeEquals("s3cr3t-value-0123456789", ""));
    }

    @Test
    void failsClosedWithoutAConfiguredSecretOrWithoutAProvidedOne() {
        assertFalse(SecretTokens.constantTimeEquals(null, "anything"));
        assertFalse(SecretTokens.constantTimeEquals("", ""));
        assertFalse(SecretTokens.constantTimeEquals("   ", "   "));
        assertFalse(SecretTokens.constantTimeEquals("s3cr3t-value-0123456789", null));
    }
}
