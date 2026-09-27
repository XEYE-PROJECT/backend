package com.xeye.backend.user.infrastructure.security;

import com.xeye.backend.shared.exception.UnauthorizedException;
import com.xeye.backend.shared.security.JwtService;
import com.xeye.backend.user.application.port.out.TokenIssuer;
import com.xeye.backend.user.domain.model.User;
import io.jsonwebtoken.JwtException;
import org.springframework.stereotype.Component;

import java.util.Map;

/** Emite JWT para el puerto {@link TokenIssuer} mediante el {@link JwtService} compartido. */
@Component
public class JwtTokenIssuer implements TokenIssuer {

    /** Vida del token del segundo factor: lo justo para teclear el código. */
    static final long MFA_TTL_SECONDS = 5 * 60;

    private final JwtService jwtService;

    public JwtTokenIssuer(JwtService jwtService) {
        this.jwtService = jwtService;
    }

    @Override
    public IssuedAccess issue(User user) {
        JwtService.IssuedToken issued = jwtService.generate(
                user.id(), user.email(), user.permission().value(), user.tokenVersion());
        return new IssuedAccess(issued.token(), jwtService.expirationMinutes());
    }

    @Override
    public String issueMfaChallenge(User user) {
        return jwtService.generateSpecial(JwtService.PURPOSE_MFA, String.valueOf(user.id()),
                Map.of("ver", user.tokenVersion()), MFA_TTL_SECONDS);
    }

    @Override
    public Long resolveMfaChallenge(String mfaToken) {
        try {
            return Long.valueOf(jwtService.parse(mfaToken, JwtService.PURPOSE_MFA).subject());
        } catch (JwtException | IllegalArgumentException | NullPointerException e) {
            throw new UnauthorizedException("Invalid or expired verification session", "INVALID_MFA_TOKEN");
        }
    }

    @Override
    public long expiresInMinutes() {
        return jwtService.expirationMinutes();
    }
}
