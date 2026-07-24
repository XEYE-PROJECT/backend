package com.xeye.backend.user.infrastructure.web.dto;

import jakarta.validation.constraints.Email;

/** Todos los campos son opcionales; solo se aplican los no nulos. */
public record UpdateUserRequest(
        String name,
        String surname,
        @Email String email,
        String password) {
}
