package com.xeye.backend.user.application.port.out;

import com.xeye.backend.user.domain.model.User;

import java.util.Optional;

/** Puerto de salida de persistencia de usuarios; devuelve dominio, nunca entidades JPA. */
public interface UserRepository {

    Optional<User> findById(Long id);

    Optional<User> findByEmail(String email);

    boolean existsByEmail(String email);

    User save(User user);

    void deleteById(Long id);
}
