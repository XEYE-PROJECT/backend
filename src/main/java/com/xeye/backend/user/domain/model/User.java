package com.xeye.backend.user.domain.model;

import java.time.Instant;
import java.util.List;
import java.util.Objects;

/**
 * Agregado User, dominio puro (sin JPA/Spring). {@code password} es siempre un hash;
 * el hasheo es un puerto ({@code PasswordHasher}) de la capa de aplicación.
 * <p>
 * Además de la identidad guarda el estado de la cuenta: verificación de email, bloqueo
 * progresivo por intentos fallidos, la versión de tokens (subirla invalida todas las sesiones),
 * el segundo factor TOTP y la identidad SSO enlazada.
 */
public class User {

    /** Intentos fallidos consecutivos a partir de los cuales se bloquea la cuenta. */
    public static final int LOCK_THRESHOLD = 5;
    /** Bloqueo máximo entre intentos (el bloqueo dobla en cada fallo: 1, 2, 4… minutos). */
    public static final int MAX_LOCK_MINUTES = 60;

    private final Long id;
    private String name;
    private String surname;
    private String email;
    private String password;
    private Permission permission;
    private boolean emailVerified;
    private String locale;
    private int tokenVersion;
    private int failedLoginCount;
    private Instant lockedUntil;
    private Instant lastLoginAt;
    private String totpSecret;
    private boolean totpEnabled;
    private List<String> recoveryCodeHashes;
    private String ssoProvider;
    private String ssoSubject;
    private final Instant createdAt;
    private final Instant updatedAt;

    public User(Long id, String name, String surname, String email, String password,
                Permission permission, boolean emailVerified, String locale, int tokenVersion,
                int failedLoginCount, Instant lockedUntil, Instant lastLoginAt,
                String totpSecret, boolean totpEnabled, List<String> recoveryCodeHashes,
                String ssoProvider, String ssoSubject, Instant createdAt, Instant updatedAt) {
        this.id = id;
        this.name = requireText(name, "name");
        this.surname = requireText(surname, "surname");
        this.email = normalizeEmail(email);
        this.password = Objects.requireNonNull(password, "password");
        this.permission = permission == null ? Permission.USER : permission;
        this.emailVerified = emailVerified;
        this.locale = normalizeLocale(locale);
        this.tokenVersion = tokenVersion;
        this.failedLoginCount = failedLoginCount;
        this.lockedUntil = lockedUntil;
        this.lastLoginAt = lastLoginAt;
        this.totpSecret = totpSecret;
        this.totpEnabled = totpEnabled;
        this.recoveryCodeHashes = recoveryCodeHashes == null ? List.of() : List.copyOf(recoveryCodeHashes);
        this.ssoProvider = ssoProvider;
        this.ssoSubject = ssoSubject;
        this.createdAt = createdAt;
        this.updatedAt = updatedAt;
    }

    /** Factoría de un usuario estándar nuevo (aún sin persistir, email sin verificar). */
    public static User register(String name, String surname, String email, String hashedPassword) {
        return register(name, surname, email, hashedPassword, "es");
    }

    public static User register(String name, String surname, String email, String hashedPassword, String locale) {
        return new User(null, name, surname, email, hashedPassword, Permission.USER, false, locale, 0,
                0, null, null, null, false, List.of(), null, null, null, null);
    }

    /**
     * Usuario creado desde un proveedor SSO: el email ya viene verificado por el proveedor y la
     * contraseña es un hash aleatorio inutilizable (puede fijar una vía "recuperar contraseña").
     */
    public static User fromSso(String name, String surname, String email, String unusablePasswordHash,
                               String locale, String provider, String subject) {
        return new User(null, name, surname, email, unusablePasswordHash, Permission.USER, true, locale, 0,
                0, null, null, null, false, List.of(), provider, subject, null, null);
    }

    public void rename(String name, String surname) {
        this.name = requireText(name, "name");
        this.surname = requireText(surname, "surname");
    }

    /** Cambia el email (ya confirmado por el usuario) y cierra todas las sesiones. */
    public void changeEmail(String email) {
        this.email = normalizeEmail(email);
        this.emailVerified = true;
        invalidateSessions();
    }

    /** Cambia la contraseña (hash) y cierra todas las sesiones. */
    public void changePassword(String hashedPassword) {
        this.password = Objects.requireNonNull(hashedPassword, "password");
        invalidateSessions();
        this.failedLoginCount = 0;
        this.lockedUntil = null;
    }

    public void changeLocale(String locale) {
        this.locale = normalizeLocale(locale);
    }

    public void promoteTo(Permission permission) {
        this.permission = Objects.requireNonNull(permission, "permission");
    }

    public void markEmailVerified() {
        this.emailVerified = true;
    }

    /** Sube la versión de tokens: cualquier JWT emitido antes deja de ser válido. */
    public void invalidateSessions() {
        this.tokenVersion++;
    }

    // ---- bloqueo progresivo ----

    public boolean isLocked(Instant now) {
        return lockedUntil != null && lockedUntil.isAfter(now);
    }

    /**
     * Registra un fallo de login. A partir de {@link #LOCK_THRESHOLD} fallos seguidos la cuenta se
     * bloquea 1, 2, 4, 8… minutos (tope {@link #MAX_LOCK_MINUTES}).
     * @return los minutos de bloqueo aplicados (0 si aún no se bloquea)
     */
    public int recordFailedLogin(Instant now) {
        failedLoginCount++;
        if (failedLoginCount < LOCK_THRESHOLD) {
            return 0;
        }
        int exponent = Math.min(failedLoginCount - LOCK_THRESHOLD, 20);
        int minutes = (int) Math.min(MAX_LOCK_MINUTES, 1L << exponent);
        lockedUntil = now.plusSeconds(minutes * 60L);
        return minutes;
    }

    public void recordSuccessfulLogin(Instant now) {
        failedLoginCount = 0;
        lockedUntil = null;
        lastLoginAt = now;
    }

    public void unlock() {
        failedLoginCount = 0;
        lockedUntil = null;
    }

    // ---- 2FA ----

    /** Guarda un secreto pendiente de confirmar (no activa el 2FA hasta {@link #enableTotp}). */
    public void stageTotpSecret(String secret) {
        if (totpEnabled) {
            throw new IllegalStateException("2FA is already enabled");
        }
        this.totpSecret = Objects.requireNonNull(secret, "secret");
    }

    public void enableTotp(List<String> recoveryCodeHashes) {
        if (totpSecret == null) {
            throw new IllegalStateException("No TOTP secret staged");
        }
        this.totpEnabled = true;
        this.recoveryCodeHashes = List.copyOf(recoveryCodeHashes);
        invalidateSessions();
    }

    public void disableTotp() {
        this.totpEnabled = false;
        this.totpSecret = null;
        this.recoveryCodeHashes = List.of();
    }

    /** Consume un código de recuperación (por hash). @return true si existía. */
    public boolean consumeRecoveryCode(String hash) {
        if (!recoveryCodeHashes.contains(hash)) {
            return false;
        }
        this.recoveryCodeHashes = recoveryCodeHashes.stream().filter(h -> !h.equals(hash)).toList();
        return true;
    }

    // ---- SSO ----

    public void linkSso(String provider, String subject) {
        this.ssoProvider = Objects.requireNonNull(provider, "provider");
        this.ssoSubject = Objects.requireNonNull(subject, "subject");
        this.emailVerified = true;
    }

    private static String requireText(String value, String field) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(field + " must not be blank");
        }
        return value.trim();
    }

    private static String normalizeEmail(String email) {
        if (email == null || email.isBlank()) {
            throw new IllegalArgumentException("email must not be blank");
        }
        return email.trim().toLowerCase();
    }

    private static String normalizeLocale(String locale) {
        return locale != null && locale.trim().toLowerCase().startsWith("en") ? "en" : "es";
    }

    public Long id() {
        return id;
    }

    public String name() {
        return name;
    }

    public String surname() {
        return surname;
    }

    public String email() {
        return email;
    }

    public String password() {
        return password;
    }

    public Permission permission() {
        return permission;
    }

    public boolean emailVerified() {
        return emailVerified;
    }

    public String locale() {
        return locale;
    }

    public int tokenVersion() {
        return tokenVersion;
    }

    public int failedLoginCount() {
        return failedLoginCount;
    }

    public Instant lockedUntil() {
        return lockedUntil;
    }

    public Instant lastLoginAt() {
        return lastLoginAt;
    }

    public String totpSecret() {
        return totpSecret;
    }

    public boolean totpEnabled() {
        return totpEnabled;
    }

    public List<String> recoveryCodeHashes() {
        return recoveryCodeHashes;
    }

    public String ssoProvider() {
        return ssoProvider;
    }

    public String ssoSubject() {
        return ssoSubject;
    }

    public Instant createdAt() {
        return createdAt;
    }

    public Instant updatedAt() {
        return updatedAt;
    }
}
