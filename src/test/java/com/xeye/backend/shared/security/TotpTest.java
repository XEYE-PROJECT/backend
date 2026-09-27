package com.xeye.backend.shared.security;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TotpTest {

    /** Vectores de RFC 6238 (SHA-1, secreto "12345678901234567890" = base32 GEZDGNBVGY3TQOJQGEZDGNBVGY3TQOJQ). */
    private static final String RFC_SECRET = "GEZDGNBVGY3TQOJQGEZDGNBVGY3TQOJQ";

    @Test
    void matchesRfc6238Vectors() {
        assertEquals("287082", Totp.codeAt(RFC_SECRET, 59));
        assertEquals("081804", Totp.codeAt(RFC_SECRET, 1111111109L));
        assertEquals("005924", Totp.codeAt(RFC_SECRET, 1234567890L));
        assertEquals("279037", Totp.codeAt(RFC_SECRET, 2000000000L));
    }

    @Test
    void verifyAcceptsAdjacentWindowsOnly() {
        long t = 1234567890L;
        String code = Totp.codeAt(RFC_SECRET, t);
        assertTrue(Totp.verify(RFC_SECRET, code, t));
        assertTrue(Totp.verify(RFC_SECRET, code, t + 30));
        assertTrue(Totp.verify(RFC_SECRET, code, t - 30));
        assertFalse(Totp.verify(RFC_SECRET, code, t + 90));
        assertFalse(Totp.verify(RFC_SECRET, "000000", t));
        assertFalse(Totp.verify(RFC_SECRET, "12345", t));
        assertFalse(Totp.verify(RFC_SECRET, null, t));
    }

    @Test
    void base32RoundTripAndSecretFormat() {
        byte[] raw = "12345678901234567890".getBytes();
        assertEquals(RFC_SECRET, Totp.base32Encode(raw));
        assertEquals(new String(raw), new String(Totp.base32Decode(RFC_SECRET)));
        String secret = Totp.generateSecret();
        assertEquals(32, secret.length());
        assertTrue(Totp.otpauthUri("XEYE", "joan@example.com", secret)
                .startsWith("otpauth://totp/XEYE%3Ajoan%40example.com?secret=" + secret + "&issuer=XEYE"));
    }
}
