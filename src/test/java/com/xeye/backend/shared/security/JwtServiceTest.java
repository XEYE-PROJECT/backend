package com.xeye.backend.shared.security;

import io.jsonwebtoken.JwtException;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

class JwtServiceTest {

    private final JwtService jwt = new JwtService(
            new JwtProperties("unit-test-secret-that-is-long-enough-0123456789", 60, "xeye-test"));

    @Test
    void accessTokenCarriesIdVersionAndPurpose() {
        JwtService.IssuedToken issued = jwt.generate(7L, "j@e.com", "admin", 3);
        JwtService.ParsedToken parsed = jwt.parse(issued.token(), JwtService.PURPOSE_ACCESS);
        assertEquals(issued.jti(), parsed.jti());
        assertEquals("7", parsed.subject());
        assertEquals(3, parsed.version());
        AuthenticatedUser user = parsed.toAuthenticatedUser();
        assertEquals(7L, user.id());
        assertEquals(issued.jti(), user.jti());
        assertNotNull(user.expiresAt());
    }

    @Test
    void specialPurposeTokensNeverAuthenticate() {
        String mfa = jwt.generateSpecial(JwtService.PURPOSE_MFA, "7", Map.of("ver", 1), 300);
        assertThrows(JwtException.class, () -> jwt.parse(mfa, JwtService.PURPOSE_ACCESS));
        assertEquals("7", jwt.parse(mfa, JwtService.PURPOSE_MFA).subject());
    }

    @Test
    void tamperedTokenIsRejected() {
        String token = jwt.generate(1L, "a@b.c", "user", 0).token();
        assertThrows(JwtException.class, () -> jwt.parse(token + "x"));
    }
}
