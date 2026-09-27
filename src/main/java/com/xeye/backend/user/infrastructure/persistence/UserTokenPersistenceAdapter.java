package com.xeye.backend.user.infrastructure.persistence;

import com.xeye.backend.user.application.port.out.UserTokenRepository;
import com.xeye.backend.user.domain.model.TokenPurpose;
import com.xeye.backend.user.domain.model.UserToken;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.Optional;

@Component
public class UserTokenPersistenceAdapter implements UserTokenRepository {

    private final UserTokenJpaRepository jpa;

    public UserTokenPersistenceAdapter(UserTokenJpaRepository jpa) {
        this.jpa = jpa;
    }

    @Override
    public UserToken save(UserToken token) {
        UserTokenJpaEntity entity = new UserTokenJpaEntity();
        entity.setId(token.id());
        entity.setUserId(token.userId());
        entity.setPurpose(token.purpose().value());
        entity.setTokenHash(token.tokenHash());
        entity.setPayload(token.payload());
        entity.setExpiresAt(token.expiresAt());
        entity.setUsedAt(token.usedAt());
        return toDomain(jpa.save(entity));
    }

    @Override
    public Optional<UserToken> findByHash(String tokenHash) {
        return jpa.findByTokenHash(tokenHash).map(UserTokenPersistenceAdapter::toDomain);
    }

    @Override
    @Transactional
    public void invalidateAll(Long userId, TokenPurpose purpose, Instant now) {
        jpa.markAllUsed(userId, purpose.value(), now);
    }

    @Override
    @Transactional
    public int deleteExpiredBefore(Instant cutoff) {
        return jpa.deleteExpiredBefore(cutoff);
    }

    private static UserToken toDomain(UserTokenJpaEntity e) {
        return new UserToken(e.getId(), e.getUserId(), TokenPurpose.fromString(e.getPurpose()), e.getTokenHash(),
                e.getPayload(), e.getExpiresAt(), e.getUsedAt(), e.getCreatedAt());
    }
}
