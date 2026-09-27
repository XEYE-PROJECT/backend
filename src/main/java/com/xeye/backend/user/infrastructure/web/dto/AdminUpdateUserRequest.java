package com.xeye.backend.user.infrastructure.web.dto;

import jakarta.validation.constraints.Pattern;

/** Todos opcionales. {@code permission}: "user" | "admin". */
public record AdminUpdateUserRequest(
        @Pattern(regexp = "user|admin") String permission,
        Boolean emailVerified,
        Boolean unlock) {
}
