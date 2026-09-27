package com.xeye.backend.user.infrastructure.security;

import com.xeye.backend.shared.security.TokenRevocationRegistry;
import com.xeye.backend.user.application.port.out.SessionRevoker;
import com.xeye.backend.user.infrastructure.persistence.RevokedTokenJpaEntity;
import com.xeye.backend.user.infrastructure.persistence.RevokedTokenJpaRepository;
import com.xeye.backend.user.infrastructure.persistence.UserJpaRepository;
import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.time.Instant;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Implementa a la vez el registro que consulta el filtro JWT ({@link TokenRevocationRegistry})
 * y el puerto que usan los servicios para revocar ({@link SessionRevoker}). Los {@code jti}
 * revocados viven en memoria (cargados de BD al arrancar) y la versión de tokens de cada usuario
 * se cachea hasta que cambia: así el filtro no toca la BD en cada petición.
 * Monolito de una instancia; con varias instancias iría a Redis o se consultaría siempre la BD.
 */
@Component
public class TokenRevocationAdapter implements TokenRevocationRegistry, SessionRevoker {

    private static final Logger log = LoggerFactory.getLogger(TokenRevocationAdapter.class);

    private final RevokedTokenJpaRepository revoked;
    private final UserJpaRepository users;
    private final Set<String> revokedJtis = ConcurrentHashMap.newKeySet();
    private final Map<Long, Integer> versionCache = new ConcurrentHashMap<>();

    public TokenRevocationAdapter(RevokedTokenJpaRepository revoked, UserJpaRepository users) {
        this.revoked = revoked;
        this.users = users;
    }

    @PostConstruct
    void load() {
        revokedJtis.addAll(revoked.findLiveJtis(Instant.now()));
        log.info("Loaded {} revoked token(s) still alive", revokedJtis.size());
    }

    @Override
    public boolean isRevoked(String jti) {
        return jti != null && revokedJtis.contains(jti);
    }

    @Override
    public boolean isCurrentVersion(Long userId, int version) {
        Integer current = versionCache.get(userId);
        if (current == null) {
            current = users.findTokenVersionById(userId).orElse(null);
            if (current == null) {
                return false; // usuario borrado: su token ya no vale
            }
            versionCache.put(userId, current);
        }
        return current == version;
    }

    @Override
    @Transactional
    public void revoke(String jti, Long userId, Instant expiresAt) {
        revokedJtis.add(jti);
        revoked.save(new RevokedTokenJpaEntity(jti, userId, expiresAt == null ? Instant.now().plusSeconds(3600) : expiresAt));
    }

    @Override
    public void versionChanged(Long userId) {
        versionCache.remove(userId);
        // Si estamos dentro de la transacción que cambia la versión, otra petición podría releer y
        // cachear el valor viejo antes del commit: se vuelve a invalidar tras el commit.
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    versionCache.remove(userId);
                }
            });
        }
    }

    @Override
    @Transactional
    public int deleteExpiredBefore(Instant cutoff) {
        int deleted = revoked.deleteExpiredBefore(cutoff);
        revokedJtis.retainAll(revoked.findLiveJtis(cutoff));
        return deleted;
    }
}
