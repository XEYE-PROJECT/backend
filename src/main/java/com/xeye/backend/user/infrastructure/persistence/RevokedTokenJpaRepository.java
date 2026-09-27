package com.xeye.backend.user.infrastructure.persistence;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

import java.time.Instant;
import java.util.List;

public interface RevokedTokenJpaRepository extends JpaRepository<RevokedTokenJpaEntity, String> {

    @Query("select r.jti from RevokedTokenJpaEntity r where r.expiresAt > :now")
    List<String> findLiveJtis(Instant now);

    @Modifying
    @Query("delete from RevokedTokenJpaEntity r where r.expiresAt < :cutoff")
    int deleteExpiredBefore(Instant cutoff);
}
