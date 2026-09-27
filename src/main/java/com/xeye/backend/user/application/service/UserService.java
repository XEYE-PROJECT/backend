package com.xeye.backend.user.application.service;

import com.xeye.backend.shared.config.AuthProperties;
import com.xeye.backend.shared.event.UserDeletedEvent;
import com.xeye.backend.shared.exception.BadRequestException;
import com.xeye.backend.shared.exception.ConflictException;
import com.xeye.backend.shared.exception.NotFoundException;
import com.xeye.backend.shared.exception.UnauthorizedException;
import com.xeye.backend.shared.security.AuthAuditLog;
import com.xeye.backend.shared.security.Totp;
import com.xeye.backend.user.application.command.AuthResult;
import com.xeye.backend.user.application.command.ChangeEmailCommand;
import com.xeye.backend.user.application.command.ChangePasswordCommand;
import com.xeye.backend.user.application.command.MfaSetup;
import com.xeye.backend.user.application.command.UpdateUserCommand;
import com.xeye.backend.user.application.port.in.UserUseCases;
import com.xeye.backend.user.application.port.out.PasswordHasher;
import com.xeye.backend.user.application.port.out.SessionRevoker;
import com.xeye.backend.user.application.port.out.UserRepository;
import com.xeye.backend.user.domain.model.User;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.SecureRandom;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/** La cuenta propia: perfil, cambio de email/contraseña (con la contraseña actual), 2FA y baja. */
@Service
public class UserService implements UserUseCases {

    private static final SecureRandom RANDOM = new SecureRandom();
    private static final int RECOVERY_CODES = 10;

    private final UserRepository users;
    private final PasswordHasher passwordHasher;
    private final PasswordValidator passwordValidator;
    private final AccountTokenService accountTokens;
    private final SessionIssuer sessions;
    private final SessionRevoker sessionRevoker;
    private final ApplicationEventPublisher events;
    private final String mfaIssuer;

    public UserService(UserRepository users, PasswordHasher passwordHasher, PasswordValidator passwordValidator,
                       AccountTokenService accountTokens, SessionIssuer sessions, SessionRevoker sessionRevoker,
                       ApplicationEventPublisher events, AuthProperties props) {
        this.users = users;
        this.passwordHasher = passwordHasher;
        this.passwordValidator = passwordValidator;
        this.accountTokens = accountTokens;
        this.sessions = sessions;
        this.sessionRevoker = sessionRevoker;
        this.events = events;
        this.mfaIssuer = props.mfaIssuer();
    }

    @Override
    @Transactional(readOnly = true)
    public User getById(Long userId) {
        return users.findById(userId)
                .orElseThrow(() -> new NotFoundException("User not found"));
    }

    @Override
    @Transactional
    public User update(Long userId, UpdateUserCommand command) {
        User user = getById(userId);
        if (command.name() != null || command.surname() != null) {
            user.rename(
                    command.name() != null ? command.name() : user.name(),
                    command.surname() != null ? command.surname() : user.surname());
        }
        if (command.locale() != null) {
            user.changeLocale(command.locale());
        }
        return users.save(user);
    }

    @Override
    @Transactional
    public void requestEmailChange(Long userId, ChangeEmailCommand command) {
        User user = getById(userId);
        requireCurrentPassword(user, command.currentPassword());
        String newEmail = normalizeEmail(command.newEmail());
        if (newEmail.equals(user.email())) {
            throw new BadRequestException("That is already your email");
        }
        if (users.existsByEmail(newEmail)) {
            // No se revela: el dueño del otro email no recibe nada y el usuario ve el mismo mensaje.
            AuthAuditLog.info("email_change_taken", "-", user.email(), "userId=" + userId);
            return;
        }
        accountTokens.sendEmailChange(user, newEmail);
        AuthAuditLog.info("email_change_requested", "-", user.email(), "userId=" + userId);
    }

    @Override
    @Transactional
    public AuthResult changePassword(Long userId, ChangePasswordCommand command) {
        User user = getById(userId);
        requireCurrentPassword(user, command.currentPassword());
        passwordValidator.require(command.newPassword(), user.email());
        user.changePassword(passwordHasher.hash(command.newPassword()));
        User saved = users.save(user);
        sessionRevoker.versionChanged(userId);
        AuthAuditLog.info("password_changed", "-", user.email(), "userId=" + userId);
        // Sesión nueva para el llamante: las demás han quedado cerradas por la versión.
        return sessions.openWithoutMfa(saved);
    }

    @Override
    @Transactional
    public MfaSetup setupMfa(Long userId, String currentPassword) {
        User user = getById(userId);
        requireCurrentPassword(user, currentPassword);
        if (user.totpEnabled()) {
            throw new ConflictException("2FA is already enabled", "MFA_ALREADY_ENABLED");
        }
        String secret = Totp.generateSecret();
        user.stageTotpSecret(secret);
        users.save(user);
        return new MfaSetup(secret, Totp.otpauthUri(mfaIssuer, user.email(), secret));
    }

    @Override
    @Transactional
    public List<String> enableMfa(Long userId, String code) {
        User user = getById(userId);
        if (user.totpEnabled()) {
            throw new ConflictException("2FA is already enabled", "MFA_ALREADY_ENABLED");
        }
        if (user.totpSecret() == null) {
            throw new BadRequestException("Start the 2FA setup first", "MFA_NOT_STAGED");
        }
        if (!Totp.verify(user.totpSecret(), code, Instant.now().getEpochSecond())) {
            throw new BadRequestException("Invalid verification code", "INVALID_MFA_CODE");
        }
        List<String> codes = new ArrayList<>(RECOVERY_CODES);
        List<String> hashes = new ArrayList<>(RECOVERY_CODES);
        for (int i = 0; i < RECOVERY_CODES; i++) {
            String c = recoveryCode();
            codes.add(c);
            hashes.add(AccountTokenService.hash(c.replace("-", "")));
        }
        user.enableTotp(hashes);
        users.save(user);
        sessionRevoker.versionChanged(userId);
        AuthAuditLog.info("mfa_enabled", "-", user.email(), "userId=" + userId);
        return codes;
    }

    @Override
    @Transactional
    public void disableMfa(Long userId, String currentPassword, String code) {
        User user = getById(userId);
        requireCurrentPassword(user, currentPassword);
        if (!user.totpEnabled()) {
            return;
        }
        String clean = code == null ? "" : code.replace(" ", "").replace("-", "").trim();
        boolean ok = Totp.verify(user.totpSecret(), clean, Instant.now().getEpochSecond())
                || user.consumeRecoveryCode(AccountTokenService.hash(clean.toUpperCase(Locale.ROOT)));
        if (!ok) {
            throw new BadRequestException("Invalid verification code", "INVALID_MFA_CODE");
        }
        user.disableTotp();
        users.save(user);
        AuthAuditLog.info("mfa_disabled", "-", user.email(), "userId=" + userId);
    }

    @Override
    @Transactional
    public void delete(Long userId) {
        User user = getById(userId);
        users.deleteById(userId);
        sessionRevoker.versionChanged(userId);
        // La BD borra en cascada api_keys/lists/elements; el search-service invalida sus cachés con este evento.
        events.publishEvent(new UserDeletedEvent(userId));
        AuthAuditLog.info("account_deleted", "-", user.email(), "userId=" + userId);
    }

    private void requireCurrentPassword(User user, String currentPassword) {
        if (currentPassword == null || !passwordHasher.matches(currentPassword, user.password())) {
            AuthAuditLog.warn("reauth_failed", "-", user.email(), "userId=" + user.id());
            throw new UnauthorizedException("Current password is incorrect", "INVALID_CURRENT_PASSWORD");
        }
    }

    /** Código de recuperación legible: 10 caracteres base32 en dos grupos (XXXXX-XXXXX). */
    private static String recoveryCode() {
        String alphabet = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789";
        StringBuilder sb = new StringBuilder(11);
        for (int i = 0; i < 10; i++) {
            if (i == 5) {
                sb.append('-');
            }
            sb.append(alphabet.charAt(RANDOM.nextInt(alphabet.length())));
        }
        return sb.toString();
    }

    private static String normalizeEmail(String email) {
        if (email == null || email.isBlank()) {
            throw new BadRequestException("Email must not be blank");
        }
        return email.trim().toLowerCase(Locale.ROOT);
    }
}
