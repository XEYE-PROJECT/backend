package com.xeye.backend.user.application.port.in;

import com.xeye.backend.user.application.command.AdminUpdateUserCommand;
import com.xeye.backend.user.application.command.UserPage;
import com.xeye.backend.user.domain.model.User;

/** Puerto de entrada de administración de cuentas (solo ROLE_ADMIN). */
public interface AdminUserUseCases {

    UserPage list(int offset, int limit);

    User get(Long userId);

    User update(Long actorId, Long userId, AdminUpdateUserCommand command);

    void delete(Long actorId, Long userId);

    void logoutAll(Long userId);
}
