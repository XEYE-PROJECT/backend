package com.xeye.backend.user.infrastructure.persistence;

import com.xeye.backend.user.domain.model.Permission;
import com.xeye.backend.user.domain.model.User;

/** Convierte entre el dominio {@link User} y su entidad JPA. */
final class UserMapper {

    private UserMapper() {
    }

    static User toDomain(UserJpaEntity entity) {
        return new User(
                entity.getId(),
                entity.getName(),
                entity.getSurname(),
                entity.getEmail(),
                entity.getPassword(),
                Permission.fromString(entity.getPermission()),
                entity.getCreatedAt(),
                entity.getUpdatedAt());
    }

    static UserJpaEntity toEntity(User user) {
        UserJpaEntity entity = new UserJpaEntity();
        entity.setId(user.id());
        entity.setName(user.name());
        entity.setSurname(user.surname());
        entity.setEmail(user.email());
        entity.setPassword(user.password());
        entity.setPermission(user.permission().value());
        return entity;
    }
}
