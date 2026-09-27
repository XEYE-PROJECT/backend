package com.xeye.backend.user.infrastructure.web.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Pattern;

/**
 * Todos opcionales. {@code permission}: "user" | "admin". {@code searchRateLimitPerMinute} fija el
 * cupo de búsquedas/minuto de la cuenta (todas sus API keys lo comparten);
 * {@code resetSearchRateLimit: true} vuelve al valor por defecto del buscador.
 */
public record AdminUpdateUserRequest(
        @Pattern(regexp = "user|admin") String permission,
        Boolean emailVerified,
        Boolean unlock,
        @Min(1) @Max(1_000_000) Integer searchRateLimitPerMinute,
        Boolean resetSearchRateLimit) {
}
