package com.xeye.backend.shared.security;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.springframework.stereotype.Service;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Date;
import java.util.UUID;

/**
 * Emite y valida tokens HS256. Los tokens de acceso llevan {@code jti} (revocable en el logout),
 * {@code ver} (versión de sesiones del usuario) y {@code purpose=access}; los tokens de
 * propósito especial (segundo factor pendiente, estado del SSO) llevan otro {@code purpose} y
 * NUNCA autentican una petición (el filtro solo acepta {@code access}).
 */
@Service
public class JwtService {

    public static final String PURPOSE_ACCESS = "access";
    public static final String PURPOSE_MFA = "mfa";
    public static final String PURPOSE_SSO_STATE = "sso_state";
    /** Dispositivo de confianza: permite saltarse el segundo factor durante un tiempo. */
    public static final String PURPOSE_MFA_TRUST = "mfa_trust";

    private final SecretKey key;
    private final long expirationMinutes;
    private final String issuer;

    public JwtService(JwtProperties props) {
        this.key = Keys.hmacShaKeyFor(props.secret().getBytes(StandardCharsets.UTF_8));
        this.expirationMinutes = props.expirationMinutes();
        this.issuer = props.issuer();
    }

    /** Token de acceso normal. */
    public IssuedToken generate(Long userId, String email, String permission, int tokenVersion) {
        Instant now = Instant.now();
        Instant expiresAt = now.plus(expirationMinutes, ChronoUnit.MINUTES);
        String jti = UUID.randomUUID().toString();
        String token = Jwts.builder()
                .id(jti)
                .subject(String.valueOf(userId))
                .issuer(issuer)
                .claim("email", email)
                .claim("permission", permission)
                .claim("ver", tokenVersion)
                .claim("purpose", PURPOSE_ACCESS)
                .issuedAt(Date.from(now))
                .expiration(Date.from(expiresAt))
                .signWith(key)
                .compact();
        return new IssuedToken(token, jti, expiresAt);
    }

    /** Token de propósito especial (no autentica): sujeto + claims libres + caducidad corta. */
    public String generateSpecial(String purpose, String subject, java.util.Map<String, Object> claims, long ttlSeconds) {
        Instant now = Instant.now();
        var builder = Jwts.builder()
                .id(UUID.randomUUID().toString())
                .subject(subject)
                .issuer(issuer)
                .claim("purpose", purpose)
                .issuedAt(Date.from(now))
                .expiration(Date.from(now.plusSeconds(ttlSeconds)));
        claims.forEach(builder::claim);
        return builder.signWith(key).compact();
    }

    /**
     * Valida firma, emisor y caducidad y devuelve el token parseado (de cualquier propósito).
     * @throws io.jsonwebtoken.JwtException si es inválido, ha expirado o está falsificado
     */
    public ParsedToken parse(String token) {
        Claims claims = Jwts.parser()
                .verifyWith(key)
                .requireIssuer(issuer)
                .build()
                .parseSignedClaims(token)
                .getPayload();
        String purpose = claims.get("purpose", String.class);
        // Gson (jjwt-gson) deserializa los números como Double: se lee como Number.
        Object rawVersion = claims.get("ver");
        int version = rawVersion instanceof Number n ? n.intValue() : 0;
        return new ParsedToken(
                claims.getId(),
                claims.getSubject(),
                claims.get("email", String.class),
                claims.get("permission", String.class),
                version,
                purpose == null ? PURPOSE_ACCESS : purpose,
                claims.getExpiration().toInstant(),
                claims);
    }

    /** Parsea exigiendo un propósito concreto (p. ej. el token del segundo factor). */
    public ParsedToken parse(String token, String expectedPurpose) {
        ParsedToken parsed = parse(token);
        if (!expectedPurpose.equals(parsed.purpose())) {
            throw new io.jsonwebtoken.JwtException("Unexpected token purpose");
        }
        return parsed;
    }

    public long expirationMinutes() {
        return expirationMinutes;
    }

    public record IssuedToken(String token, String jti, Instant expiresAt) {
    }

    public record ParsedToken(String jti, String subject, String email, String permission, int version,
                              String purpose, Instant expiresAt, Claims claims) {

        public AuthenticatedUser toAuthenticatedUser() {
            return new AuthenticatedUser(Long.valueOf(subject), email, permission, jti, expiresAt);
        }
    }
}
