package com.xeye.backend.shared.outbox;

import org.springframework.data.domain.Limit;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;

@Component
class OutboxPersistenceAdapter implements OutboxRepository {

    private static final int MAX_ERROR_LENGTH = 2000;

    private final OutboxJpaRepository jpa;

    OutboxPersistenceAdapter(OutboxJpaRepository jpa) {
        this.jpa = jpa;
    }

    /** MANDATORY: un evento fuera de la transacción del cambio que lo produjo es un bug. */
    @Override
    @Transactional(propagation = Propagation.MANDATORY)
    public void append(String type, String payload) {
        jpa.save(new OutboxJpaEntity(type, payload, Instant.now()));
    }

    @Override
    @Transactional(readOnly = true)
    public List<OutboxEvent> findDue(Instant now, int limit) {
        return jpa.findByStatusAndAvailableAtLessThanEqualOrderByIdAsc(
                        OutboxJpaEntity.STATUS_PENDING, now, Limit.of(limit))
                .stream()
                .map(e -> new OutboxEvent(e.getId(), e.getType(), e.getPayload(), e.getAttempts(),
                        e.getAvailableAt(), e.getLastError(), e.getCreatedAt()))
                .toList();
    }

    @Override
    @Transactional
    public void delete(Long id) {
        jpa.deleteById(id);
    }

    @Override
    @Transactional
    public void reschedule(Long id, int attempts, Instant availableAt, String error) {
        jpa.reschedule(id, attempts, availableAt, truncate(error));
    }

    @Override
    @Transactional
    public void markFailed(Long id, int attempts, String error) {
        jpa.markStatus(id, OutboxJpaEntity.STATUS_FAILED, attempts, truncate(error));
    }

    @Override
    @Transactional(readOnly = true)
    public long countPending() {
        return jpa.countByStatus(OutboxJpaEntity.STATUS_PENDING);
    }

    @Override
    @Transactional(readOnly = true)
    public long countFailed() {
        return jpa.countByStatus(OutboxJpaEntity.STATUS_FAILED);
    }

    private static String truncate(String error) {
        if (error == null) {
            return null;
        }
        return error.length() <= MAX_ERROR_LENGTH ? error : error.substring(0, MAX_ERROR_LENGTH);
    }
}
