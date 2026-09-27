package com.xeye.backend.user.infrastructure.web;

import com.xeye.backend.shared.config.AuthProperties;
import com.xeye.backend.shared.security.AuthenticatedUser;
import com.xeye.backend.user.application.command.LoginCommand;
import com.xeye.backend.user.application.command.MfaVerifyCommand;
import com.xeye.backend.user.application.command.RegisterUserCommand;
import com.xeye.backend.user.application.command.ResetPasswordCommand;
import com.xeye.backend.user.application.port.in.AuthUseCases;
import com.xeye.backend.user.infrastructure.sso.OidcClient;
import com.xeye.backend.user.infrastructure.web.dto.AuthConfigResponse;
import com.xeye.backend.user.infrastructure.web.dto.AuthResponse;
import com.xeye.backend.user.infrastructure.web.dto.EmailRequest;
import com.xeye.backend.user.infrastructure.web.dto.LoginRequest;
import com.xeye.backend.user.infrastructure.web.dto.LoginResponse;
import com.xeye.backend.user.infrastructure.web.dto.MessageResponse;
import com.xeye.backend.user.infrastructure.web.dto.MfaVerifyRequest;
import com.xeye.backend.user.infrastructure.web.dto.RegisterRequest;
import com.xeye.backend.user.infrastructure.web.dto.ResetPasswordRequest;
import com.xeye.backend.user.infrastructure.web.dto.TokenRequest;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * Endpoints públicos de autenticación (salvo {@code logout*}, que exigen sesión). Los que reciben
 * un email responden siempre lo mismo, exista o no la cuenta: sin enumeración de usuarios.
 */
@RestController
@RequestMapping("/auth")
public class AuthController {

    private static final String GENERIC_EMAIL_MESSAGE =
            "If the address is valid you will receive an email shortly";

    private final AuthUseCases auth;
    private final AuthProperties props;
    private final OidcClient oidc;

    public AuthController(AuthUseCases auth, AuthProperties props, OidcClient oidc) {
        this.auth = auth;
        this.props = props;
        this.oidc = oidc;
    }

    @GetMapping("/config")
    public AuthConfigResponse config() {
        return new AuthConfigResponse(props.requireEmailVerification(), oidc.enabledProviders(),
                props.captcha().provider(), props.captcha().enabled() ? props.captcha().siteKey() : null);
    }

    @PostMapping("/register")
    @ResponseStatus(HttpStatus.ACCEPTED)
    public MessageResponse register(@Valid @RequestBody RegisterRequest request, HttpServletRequest http) {
        auth.register(new RegisterUserCommand(request.name(), request.surname(), request.email(),
                request.password(), request.locale(), request.captchaToken()), http.getRemoteAddr());
        return new MessageResponse(props.requireEmailVerification()
                ? "Check your inbox to verify your email" : "Account created, you can sign in now");
    }

    @PostMapping("/login")
    public LoginResponse login(@Valid @RequestBody LoginRequest request, HttpServletRequest http) {
        return LoginResponse.of(auth.login(
                new LoginCommand(request.email(), request.password(), request.captchaToken(), request.mfaTrustToken()),
                http.getRemoteAddr()));
    }

    @PostMapping("/mfa")
    public AuthResponse mfa(@Valid @RequestBody MfaVerifyRequest request, HttpServletRequest http) {
        return AuthResponse.of(auth.verifyMfa(new MfaVerifyCommand(request.mfaToken(), request.code(),
                Boolean.TRUE.equals(request.rememberDevice())), http.getRemoteAddr()));
    }

    @PostMapping("/verify-email")
    public LoginResponse verifyEmail(@Valid @RequestBody TokenRequest request) {
        return LoginResponse.of(auth.verifyEmail(request.token()));
    }

    @PostMapping("/resend-verification")
    @ResponseStatus(HttpStatus.ACCEPTED)
    public MessageResponse resendVerification(@Valid @RequestBody EmailRequest request) {
        auth.resendVerification(request.email());
        return new MessageResponse(GENERIC_EMAIL_MESSAGE);
    }

    @PostMapping("/forgot-password")
    @ResponseStatus(HttpStatus.ACCEPTED)
    public MessageResponse forgotPassword(@Valid @RequestBody EmailRequest request) {
        auth.forgotPassword(request.email());
        return new MessageResponse(GENERIC_EMAIL_MESSAGE);
    }

    @PostMapping("/reset-password")
    public MessageResponse resetPassword(@Valid @RequestBody ResetPasswordRequest request) {
        auth.resetPassword(new ResetPasswordCommand(request.token(), request.password()));
        return new MessageResponse("Password updated, sign in with your new password");
    }

    @PostMapping("/logout")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void logout(@AuthenticationPrincipal AuthenticatedUser current) {
        auth.logout(current);
    }

    @PostMapping("/logout-all")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void logoutAll(@AuthenticationPrincipal AuthenticatedUser current) {
        auth.logoutAll(current.id());
    }
}
