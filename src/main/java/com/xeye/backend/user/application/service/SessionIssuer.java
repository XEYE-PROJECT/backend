package com.xeye.backend.user.application.service;

import com.xeye.backend.user.application.command.AuthResult;
import com.xeye.backend.user.application.command.LoginOutcome;
import com.xeye.backend.user.application.command.MfaChallenge;
import com.xeye.backend.user.application.port.out.TokenIssuer;
import com.xeye.backend.user.domain.model.User;
import org.springframework.stereotype.Component;

/** Abre sesión para un usuario ya autenticado: token de acceso, o reto 2FA si lo tiene activo. */
@Component
public class SessionIssuer {

    private final TokenIssuer tokenIssuer;

    public SessionIssuer(TokenIssuer tokenIssuer) {
        this.tokenIssuer = tokenIssuer;
    }

    public LoginOutcome open(User user) {
        if (user.totpEnabled()) {
            return new MfaChallenge(tokenIssuer.issueMfaChallenge(user));
        }
        return openWithoutMfa(user);
    }

    /** Solo cuando el segundo factor ya se ha comprobado (o la cuenta no lo tiene). */
    public AuthResult openWithoutMfa(User user) {
        TokenIssuer.IssuedAccess access = tokenIssuer.issue(user);
        return new AuthResult(user, access.token(), access.expiresInMinutes());
    }
}
