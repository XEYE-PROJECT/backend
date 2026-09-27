package com.xeye.backend.user.application.port.in;

import com.xeye.backend.user.application.command.AuthResult;
import com.xeye.backend.user.application.command.ChangeEmailCommand;
import com.xeye.backend.user.application.command.ChangePasswordCommand;
import com.xeye.backend.user.application.command.MfaSetup;
import com.xeye.backend.user.application.command.UpdateUserCommand;
import com.xeye.backend.user.domain.model.User;

import java.util.List;

/** Puerto de entrada: la cuenta propia (perfil, credenciales, 2FA). */
public interface UserUseCases {

    User getById(Long userId);

    User update(Long userId, UpdateUserCommand command);

    /** Comprueba la contraseña y envía un enlace de confirmación al NUEVO email; el cambio se aplica al confirmarlo. */
    void requestEmailChange(Long userId, ChangeEmailCommand command);

    /** Cambia la contraseña (exige la actual), cierra las demás sesiones y devuelve un token nuevo para esta. */
    AuthResult changePassword(Long userId, ChangePasswordCommand command);

    MfaSetup setupMfa(Long userId, String currentPassword);

    /** Activa el 2FA tras comprobar un código del secreto pendiente. @return los códigos de recuperación (única vez). */
    List<String> enableMfa(Long userId, String code);

    void disableMfa(Long userId, String currentPassword, String code);

    void delete(Long userId);
}
