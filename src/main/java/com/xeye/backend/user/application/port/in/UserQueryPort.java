package com.xeye.backend.user.application.port.in;

import java.util.List;

/**
 * Puerto interno para el módulo search: los cupos de búsqueda por usuario que difieren del
 * valor por defecto del buscador, para su snapshot de bootstrap. No se expone a usuarios finales.
 */
public interface UserQueryPort {

    List<UserSearchLimit> findSearchRateLimits();

    /** Búsquedas/minuto fijadas por un admin para la cuenta {@code userId}. */
    record UserSearchLimit(Long userId, int rateLimitPerMinute) {
    }
}
