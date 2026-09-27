package com.xeye.backend.user.application.port.out;

import com.xeye.backend.user.domain.model.User;

/** Puerto de salida para emitir tokens de acceso y el token efímero del segundo factor (adaptador JWT). */
public interface TokenIssuer {

    IssuedAccess issue(User user);

    /** Token corto que solo sirve para completar el login con el código TOTP. */
    String issueMfaChallenge(User user);

    /** @return el id de usuario del token del segundo factor; lanza {@code UnauthorizedException} si no vale */
    Long resolveMfaChallenge(String mfaToken);

    long expiresInMinutes();

    record IssuedAccess(String token, long expiresInMinutes) {
    }
}
