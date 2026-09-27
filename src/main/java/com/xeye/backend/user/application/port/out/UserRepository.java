package com.xeye.backend.user.application.port.out;

import com.xeye.backend.user.domain.model.User;

import java.util.List;
import java.util.Optional;

/** Puerto de salida de persistencia de usuarios; devuelve dominio, nunca entidades JPA. */
public interface UserRepository {

    Optional<User> findById(Long id);

    Optional<User> findByEmail(String email);

    Optional<User> findBySso(String provider, String subject);

    boolean existsByEmail(String email);

    /** Página ordenada por id (administración). */
    List<User> findPage(int offset, int limit);

    long count();

    /** Usuarios con un límite de búsquedas/minuto fijado (no null). */
    List<User> findWithSearchRateLimit();

    User save(User user);

    void deleteById(Long id);
}
