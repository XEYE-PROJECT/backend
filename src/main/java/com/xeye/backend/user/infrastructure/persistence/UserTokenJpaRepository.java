package com.xeye.backend.user.infrastructure.persistence;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

import java.time.Instant;
import java.util.Optional;

interface UserTokenJpaRepository extends JpaRepository<UserTokenJpaEntity, Long> {

    Optional<UserTokenJpaEntity> findByTokenHash(String tokenHash);

    @Modifying
    @Query("update UserTokenJpaEntity t set t.usedAt = :now where t.userId = :userId and t.purpose = :purpose and t.usedAt is null")
    int markAllUsed(Long userId, String purpose, Instant now);

    @Modifying
    @Query("delete from UserTokenJpaEntity t where t.expiresAt < :cutoff")
    int deleteExpiredBefore(Instant cutoff);
}
