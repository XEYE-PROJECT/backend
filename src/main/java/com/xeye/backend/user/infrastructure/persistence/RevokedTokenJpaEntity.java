package com.xeye.backend.user.infrastructure.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;

/** Lista negra de JWT por {@code jti} (logout de servidor); se purga al caducar el token. */
@Entity
@Table(name = "revoked_tokens")
public class RevokedTokenJpaEntity {

    @Id
    @Column(length = 36)
    private String jti;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(name = "expires_at", nullable = false)
    private Instant expiresAt;

    protected RevokedTokenJpaEntity() {
    }

    public RevokedTokenJpaEntity(String jti, Long userId, Instant expiresAt) {
        this.jti = jti;
        this.userId = userId;
        this.expiresAt = expiresAt;
    }

    public String getJti() {
        return jti;
    }

    public Long getUserId() {
        return userId;
    }

    public Instant getExpiresAt() {
        return expiresAt;
    }
}
