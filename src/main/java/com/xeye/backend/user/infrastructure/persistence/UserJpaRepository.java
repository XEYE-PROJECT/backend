package com.xeye.backend.user.infrastructure.persistence;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.Optional;

public interface UserJpaRepository extends JpaRepository<UserJpaEntity, Long> {

    Optional<UserJpaEntity> findByEmail(String email);

    Optional<UserJpaEntity> findBySsoProviderAndSsoSubject(String provider, String subject);

    boolean existsByEmail(String email);

    /** Solo la versión de tokens: lo consulta el filtro JWT en cada petición no cacheada. */
    @Query("select u.tokenVersion from UserJpaEntity u where u.id = :id")
    Optional<Integer> findTokenVersionById(Long id);
}
