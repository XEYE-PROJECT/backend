package com.xeye.backend.user.infrastructure.security;

import com.xeye.backend.shared.security.JwtService;
import com.xeye.backend.user.application.port.out.TokenIssuer;
import com.xeye.backend.user.domain.model.User;
import org.springframework.stereotype.Component;

/** Emite JWT para el puerto {@link TokenIssuer} mediante el {@link JwtService} compartido. */
@Component
public class JwtTokenIssuer implements TokenIssuer {

    private final JwtService jwtService;

    public JwtTokenIssuer(JwtService jwtService) {
        this.jwtService = jwtService;
    }

    @Override
    public String issue(User user) {
        return jwtService.generate(user.id(), user.email(), user.permission().value());
    }

    @Override
    public long expiresInMinutes() {
        return jwtService.expirationMinutes();
    }
}
