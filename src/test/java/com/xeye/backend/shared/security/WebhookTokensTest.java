package com.xeye.backend.shared.security;

import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class WebhookTokensTest {

    private static final String SECRET = "it-webhook-secret-0123456789abcdef0123456789";

    @Test
    void issuesTheCanonicalTokenOfTheContractFixture() {
        // El mismo valor que lleva src/test/resources/contracts/training-job.json (HMAC del id 42).
        assertEquals("42.288039d92fa1aa710434aa348516a33ed4cc265eb7206aa9f9199c0c284c7402",
                WebhookTokens.issue(SECRET, 42));
    }

    @Test
    void verifyReturnsTheTrainingThatSignedTheToken() {
        String token = WebhookTokens.issue(SECRET, 7);
        assertEquals(Optional.of(7L), WebhookTokens.verify(SECRET, token));
        assertNotEquals(WebhookTokens.issue(SECRET, 8), token);
    }

    @Test
    void tamperedOrForeignTokensAreRejected() {
        String token = WebhookTokens.issue(SECRET, 7);
        String signature = token.substring(token.indexOf('.') + 1);
        for (String bad : new String[]{null, "", "7", "7.", ".abc", "8." + signature, "7." + signature + "00",
            "7.zz" + signature.substring(2), "x." + signature, SECRET}) {
            assertTrue(WebhookTokens.verify(SECRET, bad).isEmpty(), "token=" + bad);
        }
        assertTrue(WebhookTokens.verify("other-secret-0123456789abcdef0123456789", token).isEmpty());
        assertTrue(WebhookTokens.verify(" ", token).isEmpty());
    }
}
