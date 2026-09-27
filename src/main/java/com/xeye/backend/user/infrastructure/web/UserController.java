package com.xeye.backend.user.infrastructure.web;

import com.xeye.backend.shared.security.AuthenticatedUser;
import com.xeye.backend.user.application.command.ChangeEmailCommand;
import com.xeye.backend.user.application.command.ChangePasswordCommand;
import com.xeye.backend.user.application.command.MfaSetup;
import com.xeye.backend.user.application.command.UpdateUserCommand;
import com.xeye.backend.user.application.port.in.UserUseCases;
import com.xeye.backend.user.infrastructure.web.dto.AuthResponse;
import com.xeye.backend.user.infrastructure.web.dto.ChangeEmailRequest;
import com.xeye.backend.user.infrastructure.web.dto.ChangePasswordRequest;
import com.xeye.backend.user.infrastructure.web.dto.MessageResponse;
import com.xeye.backend.user.infrastructure.web.dto.MfaCodeRequest;
import com.xeye.backend.user.infrastructure.web.dto.MfaDisableRequest;
import com.xeye.backend.user.infrastructure.web.dto.MfaSetupRequest;
import com.xeye.backend.user.infrastructure.web.dto.MfaSetupResponse;
import com.xeye.backend.user.infrastructure.web.dto.RecoveryCodesResponse;
import com.xeye.backend.user.infrastructure.web.dto.UpdateUserRequest;
import com.xeye.backend.user.infrastructure.web.dto.UserResponse;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/** El usuario autenticado solo gestiona su propia cuenta (/users/me). */
@RestController
@RequestMapping("/users/me")
public class UserController {

    private final UserUseCases users;

    public UserController(UserUseCases users) {
        this.users = users;
    }

    @GetMapping
    public UserResponse me(@AuthenticationPrincipal AuthenticatedUser current) {
        return UserResponse.from(users.getById(current.id()));
    }

    @PutMapping
    public UserResponse update(@AuthenticationPrincipal AuthenticatedUser current,
                               @Valid @RequestBody UpdateUserRequest request) {
        return UserResponse.from(users.update(current.id(),
                new UpdateUserCommand(request.name(), request.surname(), request.locale())));
    }

    /** Envía el enlace de confirmación al nuevo email; el cambio se aplica al confirmarlo (y cierra las sesiones). */
    @PutMapping("/email")
    @ResponseStatus(HttpStatus.ACCEPTED)
    public MessageResponse changeEmail(@AuthenticationPrincipal AuthenticatedUser current,
                                       @Valid @RequestBody ChangeEmailRequest request) {
        users.requestEmailChange(current.id(), new ChangeEmailCommand(request.email(), request.currentPassword()));
        return new MessageResponse("Check the new address to confirm the change");
    }

    /** Cambia la contraseña; devuelve un token nuevo porque los anteriores quedan invalidados. */
    @PutMapping("/password")
    public AuthResponse changePassword(@AuthenticationPrincipal AuthenticatedUser current,
                                       @Valid @RequestBody ChangePasswordRequest request) {
        return AuthResponse.of(users.changePassword(current.id(),
                new ChangePasswordCommand(request.currentPassword(), request.newPassword())));
    }

    @PostMapping("/mfa/setup")
    public MfaSetupResponse setupMfa(@AuthenticationPrincipal AuthenticatedUser current,
                                     @Valid @RequestBody MfaSetupRequest request) {
        MfaSetup setup = users.setupMfa(current.id(), request.currentPassword());
        return new MfaSetupResponse(setup.secret(), setup.otpauthUri());
    }

    @PostMapping("/mfa/enable")
    public RecoveryCodesResponse enableMfa(@AuthenticationPrincipal AuthenticatedUser current,
                                           @Valid @RequestBody MfaCodeRequest request) {
        return new RecoveryCodesResponse(users.enableMfa(current.id(), request.code()));
    }

    @PostMapping("/mfa/disable")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void disableMfa(@AuthenticationPrincipal AuthenticatedUser current,
                           @Valid @RequestBody MfaDisableRequest request) {
        users.disableMfa(current.id(), request.currentPassword(), request.code());
    }

    @DeleteMapping
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@AuthenticationPrincipal AuthenticatedUser current) {
        users.delete(current.id());
    }
}
