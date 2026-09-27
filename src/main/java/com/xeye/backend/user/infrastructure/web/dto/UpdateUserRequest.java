package com.xeye.backend.user.infrastructure.web.dto;

import jakarta.validation.constraints.Size;

/** Perfil: campos opcionales, solo se aplican los no nulos. Email y contraseña tienen endpoints propios. */
public record UpdateUserRequest(
        @Size(max = 100) String name,
        @Size(max = 100) String surname,
        @Size(max = 5) String locale) {
}
