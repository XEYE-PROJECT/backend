package com.xeye.backend.user.application.service;

import com.xeye.backend.shared.config.AuthProperties;
import com.xeye.backend.shared.email.EmailSender;
import com.xeye.backend.user.application.port.out.UserTokenRepository;
import com.xeye.backend.user.domain.model.TokenPurpose;
import com.xeye.backend.user.domain.model.User;
import com.xeye.backend.user.domain.model.UserToken;
import org.springframework.stereotype.Service;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Instant;
import java.util.Base64;
import java.util.HexFormat;
import java.util.Optional;

/**
 * Emite los tokens de un solo uso que viajan por email y construye/envía esos emails.
 * El valor en claro (32 bytes aleatorios, base64url) solo existe en el enlace; en BD va el
 * SHA-256. Emitir uno nuevo invalida los anteriores del mismo propósito.
 */
@Service
public class AccountTokenService {

    private static final SecureRandom RANDOM = new SecureRandom();

    private final UserTokenRepository tokens;
    private final EmailSender email;
    private final String frontendUrl;

    public AccountTokenService(UserTokenRepository tokens, EmailSender email, AuthProperties props) {
        this.tokens = tokens;
        this.email = email;
        this.frontendUrl = props.frontendUrl().replaceAll("/+$", "");
    }

    public void sendVerification(User user) {
        String token = issue(user, TokenPurpose.VERIFY_EMAIL, null);
        email.send(EmailTemplates.verifyEmail(user.email(), user.locale(), link("/verify-email", token)));
    }

    public void sendAccountExists(User user) {
        email.send(EmailTemplates.accountExists(user.email(), user.locale(),
                frontendUrl + "/login", frontendUrl + "/forgot-password"));
    }

    public void sendPasswordReset(User user) {
        String token = issue(user, TokenPurpose.RESET_PASSWORD, null);
        email.send(EmailTemplates.resetPassword(user.email(), user.locale(), link("/reset-password", token)));
    }

    public void sendEmailChange(User user, String newEmail) {
        String token = issue(user, TokenPurpose.CHANGE_EMAIL, newEmail);
        email.send(EmailTemplates.changeEmail(newEmail, user.locale(), link("/verify-email", token)));
    }

    /** Busca un token vivo por su valor en claro. */
    public Optional<UserToken> findUsable(String rawToken, Instant now) {
        if (rawToken == null || rawToken.isBlank()) {
            return Optional.empty();
        }
        return tokens.findByHash(hash(rawToken.trim())).filter(t -> t.isUsable(now));
    }

    /** Marca el token como usado e invalida cualquier otro del mismo propósito. */
    public void consume(UserToken token, Instant now) {
        tokens.save(token.markUsed(now));
        tokens.invalidateAll(token.userId(), token.purpose(), now);
    }

    private String issue(User user, TokenPurpose purpose, String payload) {
        Instant now = Instant.now();
        tokens.invalidateAll(user.id(), purpose, now);
        byte[] bytes = new byte[32];
        RANDOM.nextBytes(bytes);
        String raw = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
        tokens.save(UserToken.issue(user.id(), purpose, hash(raw), payload, now));
        return raw;
    }

    private String link(String path, String token) {
        return frontendUrl + path + "?token=" + URLEncoder.encode(token, StandardCharsets.UTF_8);
    }

    static String hash(String raw) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(raw.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 unavailable", e);
        }
    }
}
