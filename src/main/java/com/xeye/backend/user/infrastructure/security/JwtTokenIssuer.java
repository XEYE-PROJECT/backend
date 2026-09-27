package com.xeye.backend.user.infrastructure.security;

import com.xeye.backend.shared.exception.UnauthorizedException;
import com.xeye.backend.shared.config.AuthProperties;
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
    private final int mfaTrustDays;

    public JwtTokenIssuer(JwtService jwtService, AuthProperties props) {
        this.jwtService = jwtService;
        this.mfaTrustDays = props.mfaTrustDays();
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

    /**
     * El token de confianza lleva la versión de sesión del usuario: cambiar la contraseña, cerrar
     * todas las sesiones o reconfigurar el 2FA vuelve a exigir el código en todos los dispositivos.
     */
    @Override
    public String issueMfaTrust(User user) {
        if (mfaTrustDays <= 0) {
            return null;
        }
        return jwtService.generateSpecial(JwtService.PURPOSE_MFA_TRUST, String.valueOf(user.id()),
                Map.of("ver", user.tokenVersion()), mfaTrustDays * 24L * 3600L);
    }

    @Override
    public boolean isMfaTrusted(User user, String trustToken) {
        if (trustToken == null || trustToken.isBlank() || mfaTrustDays <= 0) {
            return false;
        }
        try {
            JwtService.ParsedToken parsed = jwtService.parse(trustToken, JwtService.PURPOSE_MFA_TRUST);
            return String.valueOf(user.id()).equals(parsed.subject()) && parsed.version() == user.tokenVersion();
        } catch (JwtException | IllegalArgumentException e) {
            return false;
        }
    }

    @Override
    public long expiresInMinutes() {
        return jwtService.expirationMinutes();
    }
}
