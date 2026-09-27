package com.xeye.backend.user.application.port.out;

import com.xeye.backend.user.domain.model.TokenPurpose;
import com.xeye.backend.user.domain.model.UserToken;

import java.time.Instant;
import java.util.Optional;

/** Tokens de un solo uso (por hash). */
public interface UserTokenRepository {

    UserToken save(UserToken token);

    Optional<UserToken> findByHash(String tokenHash);

    /** Marca como usados todos los tokens vivos de ese usuario y propósito (al emitir/consumir uno nuevo). */
    void invalidateAll(Long userId, TokenPurpose purpose, Instant now);

    int deleteExpiredBefore(Instant cutoff);
}
