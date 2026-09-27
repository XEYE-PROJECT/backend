package com.xeye.backend.user.application.port.in;

import com.xeye.backend.shared.security.AuthenticatedUser;
import com.xeye.backend.user.application.command.AuthResult;
import com.xeye.backend.user.application.command.LoginCommand;
import com.xeye.backend.user.application.command.LoginOutcome;
import com.xeye.backend.user.application.command.MfaVerified;
import com.xeye.backend.user.application.command.MfaVerifyCommand;
import com.xeye.backend.user.application.command.RegisterUserCommand;
import com.xeye.backend.user.application.command.ResetPasswordCommand;
import com.xeye.backend.user.application.command.SsoIdentity;

/** Puerto de entrada: registro, login (contraseña, 2FA, SSO), verificación de email, recuperación y logout. */
public interface AuthUseCases {

    /**
     * Registra la cuenta y envía el email de verificación. No devuelve nada a propósito: si el
     * email ya existe se avisa por email al dueño y la respuesta es idéntica (sin enumeración).
     */
    void register(RegisterUserCommand command, String remoteIp);

    LoginOutcome login(LoginCommand command, String remoteIp);

    MfaVerified verifyMfa(MfaVerifyCommand command, String remoteIp);

    /** Consume el token del enlace (verificación de registro o confirmación de cambio de email) y abre sesión. */
    LoginOutcome verifyEmail(String token);

    void resendVerification(String email);

    void forgotPassword(String email);

    /** Fija la contraseña, verifica el email de paso y cierra todas las sesiones anteriores. */
    void resetPassword(ResetPasswordCommand command);

    LoginOutcome loginWithSso(SsoIdentity identity, String locale, String remoteIp);

    /** Revoca el token actual. */
    void logout(AuthenticatedUser current);

    /** Cierra todas las sesiones del usuario (incluida la actual). */
    void logoutAll(Long userId);
}
