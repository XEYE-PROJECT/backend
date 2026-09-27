package com.xeye.backend.shared.outbox;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import org.hibernate.annotations.Generated;
import org.hibernate.generator.EventType;

import java.time.Instant;

@Entity
@Table(name = "outbox_events")
class OutboxJpaEntity {

    static final String STATUS_PENDING = "pending";
    static final String STATUS_FAILED = "failed";

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 60)
    private String type;

    @Column(nullable = false, columnDefinition = "json")
    private String payload;

    @Column(nullable = false, length = 20)
    private String status = STATUS_PENDING;

    @Column(nullable = false)
    private int attempts;

    @Column(name = "available_at", nullable = false)
    private Instant availableAt;

    @Column(name = "last_error", columnDefinition = "text")
    private String lastError;

    @Generated(event = EventType.INSERT)
    @Column(name = "created_at", insertable = false, updatable = false)
    private Instant createdAt;

    protected OutboxJpaEntity() {
    }

    OutboxJpaEntity(String type, String payload, Instant availableAt) {
        this.type = type;
        this.payload = payload;
        this.availableAt = availableAt;
    }

    Long getId() {
        return id;
    }

    String getType() {
        return type;
    }

    String getPayload() {
        return payload;
    }

    int getAttempts() {
        return attempts;
    }

    Instant getAvailableAt() {
        return availableAt;
    }

    String getLastError() {
        return lastError;
    }

    Instant getCreatedAt() {
        return createdAt;
    }
}
