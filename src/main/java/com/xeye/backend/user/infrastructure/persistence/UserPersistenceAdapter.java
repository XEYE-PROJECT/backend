package com.xeye.backend.user.infrastructure.persistence;

import com.xeye.backend.user.application.port.out.UserRepository;
import com.xeye.backend.user.domain.model.User;
import org.springframework.stereotype.Component;

import java.util.Optional;

/** Adapta Spring Data JPA al puerto {@link UserRepository}. */
@Component
public class UserPersistenceAdapter implements UserRepository {

    private final UserJpaRepository jpa;

    public UserPersistenceAdapter(UserJpaRepository jpa) {
        this.jpa = jpa;
    }

    @Override
    public Optional<User> findById(Long id) {
        return jpa.findById(id).map(UserMapper::toDomain);
    }

    @Override
    public Optional<User> findByEmail(String email) {
        return jpa.findByEmail(email).map(UserMapper::toDomain);
    }

    @Override
    public boolean existsByEmail(String email) {
        return jpa.existsByEmail(email);
    }

    @Override
    public User save(User user) {
        return UserMapper.toDomain(jpa.save(UserMapper.toEntity(user)));
    }

    @Override
    public void deleteById(Long id) {
        jpa.deleteById(id);
    }
}
