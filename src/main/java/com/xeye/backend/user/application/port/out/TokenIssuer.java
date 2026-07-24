package com.xeye.backend.user.application.port.out;

import com.xeye.backend.user.domain.model.User;

/** Puerto de salida para emitir tokens de acceso (adaptador JWT en infraestructura). */
public interface TokenIssuer {

    String issue(User user);

    long expiresInMinutes();
}
