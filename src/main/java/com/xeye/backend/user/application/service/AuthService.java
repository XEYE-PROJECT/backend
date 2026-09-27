package com.xeye.backend.user.application.service;

import com.xeye.backend.shared.config.AuthProperties;
import com.xeye.backend.shared.exception.BadRequestException;
import com.xeye.backend.shared.exception.DomainException;
import com.xeye.backend.shared.exception.ForbiddenException;
import com.xeye.backend.shared.exception.TooManyRequestsException;
import com.xeye.backend.shared.exception.UnauthorizedException;
import com.xeye.backend.shared.security.AuthAuditLog;
import com.xeye.backend.shared.security.AuthenticatedUser;
import com.xeye.backend.shared.security.Totp;
import com.xeye.backend.user.application.command.AuthResult;
import com.xeye.backend.user.application.command.LoginCommand;
import com.xeye.backend.user.application.command.LoginOutcome;
import com.xeye.backend.user.application.command.MfaVerifyCommand;
import com.xeye.backend.user.application.command.RegisterUserCommand;
import com.xeye.backend.user.application.command.ResetPasswordCommand;
import com.xeye.backend.user.application.command.SsoIdentity;
import com.xeye.backend.user.application.port.in.AuthUseCases;
import com.xeye.backend.user.application.port.out.CaptchaVerifier;
import com.xeye.backend.user.application.port.out.PasswordHasher;
import com.xeye.backend.user.application.port.out.SessionRevoker;
import com.xeye.backend.user.application.port.out.TokenIssuer;
import com.xeye.backend.user.application.port.out.UserRepository;
import com.xeye.backend.user.domain.model.Permission;
import com.xeye.backend.user.domain.model.TokenPurpose;
import com.xeye.backend.user.domain.model.User;
import com.xeye.backend.user.domain.model.UserToken;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.SecureRandom;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

/**
 * Registro, login y recuperación. Principios: respuestas idénticas exista o no la cuenta
 * (sin enumeración de emails), BCrypt siempre (también cuando el usuario no existe, para no
 * filtrar por tiempo), bloqueo progresivo por cuenta, verificación de email antes del primer
 * login y todo evento relevante en el log de auditoría.
 */
@Service
public class AuthService implements AuthUseCases {

    /** Hash de una contraseña inexistente: se compara contra él cuando el email no existe. */
    private static final SecureRandom RANDOM = new SecureRandom();

    private final UserRepository users;
    private final PasswordHasher passwordHasher;
    private final PasswordValidator passwordValidator;
    private final CaptchaVerifier captcha;
    private final AccountTokenService accountTokens;
    private final SessionIssuer sessions;
    private final TokenIssuer tokenIssuer;
    private final SessionRevoker sessionRevoker;
    private final AuthProperties props;
    private final String dummyHash;

    public AuthService(UserRepository users, PasswordHasher passwordHasher, PasswordValidator passwordValidator,
                       CaptchaVerifier captcha, AccountTokenService accountTokens, SessionIssuer sessions,
                       TokenIssuer tokenIssuer, SessionRevoker sessionRevoker, AuthProperties props) {
        this.users = users;
        this.passwordHasher = passwordHasher;
        this.passwordValidator = passwordValidator;
        this.captcha = captcha;
        this.accountTokens = accountTokens;
        this.sessions = sessions;
        this.tokenIssuer = tokenIssuer;
        this.sessionRevoker = sessionRevoker;
        this.props = props;
        this.dummyHash = passwordHasher.hash(randomSecret());
    }

    @Override
    @Transactional
    public void register(RegisterUserCommand command, String remoteIp) {
        requireCaptcha(command.captchaToken(), remoteIp);
        String email = normalizeEmail(command.email());
        passwordValidator.require(command.rawPassword(), email);
        Optional<User> existing = users.findByEmail(email);
        if (existing.isPresent()) {
            // Misma respuesta que un registro válido; el dueño real recibe un aviso por email.
            AuthAuditLog.info("register_existing_email", remoteIp, email, null);
            accountTokens.sendAccountExists(existing.get());
            return;
        }
        User user = users.save(User.register(command.name(), command.surname(), email,
                passwordHasher.hash(command.rawPassword()), command.locale()));
        if (props.requireEmailVerification()) {
            accountTokens.sendVerification(user);
        } else {
            user.markEmailVerified();
            users.save(user);
        }
        AuthAuditLog.info("register", remoteIp, email, "userId=" + user.id());
    }

    @Override
    // noRollbackFor: el contador de fallos y el bloqueo deben persistir aunque la petición acabe en 401/429.
    @Transactional(noRollbackFor = DomainException.class)
    public LoginOutcome login(LoginCommand command, String remoteIp) {
        requireCaptcha(command.captchaToken(), remoteIp);
        String email = normalizeEmail(command.email());
        Instant now = Instant.now();
        Optional<User> found = users.findByEmail(email);
        if (found.isEmpty()) {
            passwordHasher.matches(command.rawPassword(), dummyHash); // mismo coste que un usuario real
            AuthAuditLog.warn("login_failed", remoteIp, email, "reason=unknown_email");
            throw invalidCredentials();
        }
        User user = found.get();
        if (user.isLocked(now)) {
            long retry = Duration.between(now, user.lockedUntil()).toSeconds();
            AuthAuditLog.warn("login_locked", remoteIp, email, "retryAfter=" + retry);
            throw new TooManyRequestsException("Account temporarily locked, try again later", "ACCOUNT_LOCKED", retry);
        }
        if (!passwordHasher.matches(command.rawPassword(), user.password())) {
            int lockMinutes = user.recordFailedLogin(now);
            users.save(user);
            AuthAuditLog.warn("login_failed", remoteIp, email,
                    "reason=bad_password failed=" + user.failedLoginCount() + (lockMinutes > 0 ? " lockedMinutes=" + lockMinutes : ""));
            throw invalidCredentials();
        }
        if (props.requireEmailVerification() && !user.emailVerified()) {
            AuthAuditLog.info("login_unverified", remoteIp, email, null);
            throw new ForbiddenException("Email not verified", "EMAIL_NOT_VERIFIED");
        }
        promoteIfBootstrapAdmin(user);
        user.recordSuccessfulLogin(now);
        users.save(user);
        LoginOutcome outcome = sessions.open(user);
        AuthAuditLog.info(outcome instanceof AuthResult ? "login" : "login_mfa_pending", remoteIp, email, "userId=" + user.id());
        return outcome;
    }

    @Override
    @Transactional(noRollbackFor = DomainException.class)
    public AuthResult verifyMfa(MfaVerifyCommand command, String remoteIp) {
        Long userId = tokenIssuer.resolveMfaChallenge(command.mfaToken());
        User user = users.findById(userId).orElseThrow(this::invalidCredentials);
        Instant now = Instant.now();
        if (!user.totpEnabled()) {
            return sessions.openWithoutMfa(user);
        }
        if (user.isLocked(now)) {
            long retry = Duration.between(now, user.lockedUntil()).toSeconds();
            throw new TooManyRequestsException("Account temporarily locked, try again later", "ACCOUNT_LOCKED", retry);
        }
        String code = command.code() == null ? "" : command.code().replace(" ", "").replace("-", "").trim();
        boolean ok = Totp.verify(user.totpSecret(), code, now.getEpochSecond())
                || user.consumeRecoveryCode(AccountTokenService.hash(code.toUpperCase(Locale.ROOT)));
        if (!ok) {
            user.recordFailedLogin(now);
            users.save(user);
            AuthAuditLog.warn("mfa_failed", remoteIp, user.email(), "failed=" + user.failedLoginCount());
            throw new UnauthorizedException("Invalid verification code", "INVALID_MFA_CODE");
        }
        user.recordSuccessfulLogin(now);
        users.save(user);
        AuthAuditLog.info("login", remoteIp, user.email(), "userId=" + user.id() + " mfa=true");
        return sessions.openWithoutMfa(user);
    }

    @Override
    @Transactional
    public LoginOutcome verifyEmail(String rawToken) {
        Instant now = Instant.now();
        UserToken token = accountTokens.findUsable(rawToken, now)
                .filter(t -> t.purpose() == TokenPurpose.VERIFY_EMAIL || t.purpose() == TokenPurpose.CHANGE_EMAIL)
                .orElseThrow(() -> new BadRequestException("Invalid or expired link", "INVALID_TOKEN"));
        User user = users.findById(token.userId())
                .orElseThrow(() -> new BadRequestException("Invalid or expired link", "INVALID_TOKEN"));
        if (token.purpose() == TokenPurpose.CHANGE_EMAIL) {
            String newEmail = normalizeEmail(token.payload());
            if (users.findByEmail(newEmail).filter(other -> !other.id().equals(user.id())).isPresent()) {
                throw new BadRequestException("That email is no longer available", "EMAIL_TAKEN");
            }
            user.changeEmail(newEmail);
            sessionRevoker.versionChanged(user.id());
            AuthAuditLog.info("email_changed", "-", newEmail, "userId=" + user.id());
        } else {
            user.markEmailVerified();
            AuthAuditLog.info("email_verified", "-", user.email(), "userId=" + user.id());
        }
        accountTokens.consume(token, now);
        promoteIfBootstrapAdmin(user);
        user.recordSuccessfulLogin(now);
        return sessions.open(users.save(user));
    }

    @Override
    @Transactional
    public void resendVerification(String email) {
        users.findByEmail(normalizeEmail(email))
                .filter(user -> !user.emailVerified())
                .ifPresent(accountTokens::sendVerification);
    }

    @Override
    @Transactional
    public void forgotPassword(String email) {
        users.findByEmail(normalizeEmail(email)).ifPresent(user -> {
            accountTokens.sendPasswordReset(user);
            AuthAuditLog.info("password_reset_requested", "-", user.email(), null);
        });
    }

    @Override
    @Transactional
    public void resetPassword(ResetPasswordCommand command) {
        Instant now = Instant.now();
        UserToken token = accountTokens.findUsable(command.token(), now)
                .filter(t -> t.purpose() == TokenPurpose.RESET_PASSWORD)
                .orElseThrow(() -> new BadRequestException("Invalid or expired link", "INVALID_TOKEN"));
        User user = users.findById(token.userId())
                .orElseThrow(() -> new BadRequestException("Invalid or expired link", "INVALID_TOKEN"));
        passwordValidator.require(command.newPassword(), user.email());
        user.changePassword(passwordHasher.hash(command.newPassword()));
        user.markEmailVerified(); // ha demostrado controlar el buzón
        users.save(user);
        accountTokens.consume(token, now);
        sessionRevoker.versionChanged(user.id());
        AuthAuditLog.info("password_reset", "-", user.email(), "userId=" + user.id());
    }

    @Override
    @Transactional
    public LoginOutcome loginWithSso(SsoIdentity identity, String locale, String remoteIp) {
        Instant now = Instant.now();
        String email = normalizeEmail(identity.email());
        User user = users.findBySso(identity.provider(), identity.subject()).orElse(null);
        if (user == null) {
            Optional<User> byEmail = users.findByEmail(email);
            if (byEmail.isPresent()) {
                if (!identity.emailVerified()) {
                    throw new ForbiddenException("The identity provider has not verified this email", "SSO_EMAIL_UNVERIFIED");
                }
                user = byEmail.get();
                user.linkSso(identity.provider(), identity.subject());
                AuthAuditLog.info("sso_linked", remoteIp, email, "provider=" + identity.provider() + " userId=" + user.id());
            } else {
                if (!identity.emailVerified()) {
                    throw new ForbiddenException("The identity provider has not verified this email", "SSO_EMAIL_UNVERIFIED");
                }
                user = users.save(User.fromSso(
                        blankTo(identity.name(), "User"), blankTo(identity.surname(), "-"), email,
                        passwordHasher.hash(randomSecret()), locale, identity.provider(), identity.subject()));
                AuthAuditLog.info("register_sso", remoteIp, email, "provider=" + identity.provider() + " userId=" + user.id());
            }
        }
        if (user.isLocked(now)) {
            long retry = Duration.between(now, user.lockedUntil()).toSeconds();
            throw new TooManyRequestsException("Account temporarily locked, try again later", "ACCOUNT_LOCKED", retry);
        }
        promoteIfBootstrapAdmin(user);
        user.recordSuccessfulLogin(now);
        LoginOutcome outcome = sessions.open(users.save(user));
        AuthAuditLog.info("login_sso", remoteIp, email, "provider=" + identity.provider() + " userId=" + user.id());
        return outcome;
    }

    @Override
    public void logout(AuthenticatedUser current) {
        if (current.jti() != null) {
            sessionRevoker.revoke(current.jti(), current.id(), current.expiresAt());
            AuthAuditLog.info("logout", "-", current.email(), "jti=" + current.jti());
        }
    }

    @Override
    @Transactional
    public void logoutAll(Long userId) {
        users.findById(userId).ifPresent(user -> {
            user.invalidateSessions();
            users.save(user);
            sessionRevoker.versionChanged(userId);
            AuthAuditLog.info("logout_all", "-", user.email(), "userId=" + userId);
        });
    }

    private void promoteIfBootstrapAdmin(User user) {
        List<String> admins = props.adminEmails();
        if (user.permission() != Permission.ADMIN && admins.stream()
                .anyMatch(a -> a.trim().equalsIgnoreCase(user.email()))) {
            user.promoteTo(Permission.ADMIN);
            AuthAuditLog.info("admin_bootstrap", "-", user.email(), "userId=" + user.id());
        }
    }

    private void requireCaptcha(String token, String remoteIp) {
        if (captcha.enabled() && !captcha.verify(token, remoteIp)) {
            throw new BadRequestException("CAPTCHA verification failed", "CAPTCHA_FAILED");
        }
    }

    private UnauthorizedException invalidCredentials() {
        return new UnauthorizedException("Invalid credentials", "INVALID_CREDENTIALS");
    }

    private static String normalizeEmail(String email) {
        if (email == null || email.isBlank()) {
            throw new BadRequestException("Email must not be blank");
        }
        return email.trim().toLowerCase(Locale.ROOT);
    }

    private static String blankTo(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value;
    }

    private static String randomSecret() {
        byte[] bytes = new byte[32];
        RANDOM.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }
}
