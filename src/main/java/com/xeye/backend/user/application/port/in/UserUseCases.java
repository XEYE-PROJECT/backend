package com.xeye.backend.user.application.port.in;

import com.xeye.backend.user.application.command.AuthResult;
import com.xeye.backend.user.application.command.LoginCommand;
import com.xeye.backend.user.application.command.RegisterUserCommand;
import com.xeye.backend.user.application.command.UpdateUserCommand;
import com.xeye.backend.user.domain.model.User;

/** Puerto de entrada: todas las operaciones sobre usuarios/cuentas. */
public interface UserUseCases {

    AuthResult register(RegisterUserCommand command);

    AuthResult login(LoginCommand command);

    User getById(Long userId);

    User update(Long userId, UpdateUserCommand command);

    void delete(Long userId);
}
