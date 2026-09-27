-- Cuentas y autenticación (sección B del backlog):
--   * verificación de email, bloqueo progresivo, invalidación de sesiones (token_version),
--     2FA TOTP, SSO OIDC e idioma del usuario sobre `users`;
--   * `user_tokens`: tokens de un solo uso (verificar email, recuperar contraseña, cambiar email)
--     guardados por hash SHA-256, con caducidad y marca de uso;
--   * `revoked_tokens`: lista negra de JWT (jti) para el logout de servidor.
-- Los usuarios existentes se dan por verificados: se registraron antes de que existiera la
-- verificación y no deben quedar bloqueados.

ALTER TABLE users
    ADD COLUMN email_verified      BOOLEAN      NOT NULL DEFAULT FALSE AFTER permission,
    ADD COLUMN locale              VARCHAR(5)   NOT NULL DEFAULT 'es' AFTER email_verified,
    ADD COLUMN token_version       INT          NOT NULL DEFAULT 0 AFTER locale,
    ADD COLUMN failed_login_count  INT          NOT NULL DEFAULT 0 AFTER token_version,
    ADD COLUMN locked_until        DATETIME     NULL AFTER failed_login_count,
    ADD COLUMN last_login_at       DATETIME     NULL AFTER locked_until,
    ADD COLUMN totp_secret         VARCHAR(64)  NULL AFTER last_login_at,
    ADD COLUMN totp_enabled        BOOLEAN      NOT NULL DEFAULT FALSE AFTER totp_secret,
    ADD COLUMN recovery_codes      TEXT         NULL AFTER totp_enabled,
    ADD COLUMN sso_provider        VARCHAR(20)  NULL AFTER recovery_codes,
    ADD COLUMN sso_subject         VARCHAR(255) NULL AFTER sso_provider;

UPDATE users SET email_verified = TRUE;

ALTER TABLE users
    ADD CONSTRAINT uq_users_sso UNIQUE (sso_provider, sso_subject);

CREATE TABLE user_tokens (
    id          BIGINT       NOT NULL AUTO_INCREMENT,
    user_id     BIGINT       NOT NULL,
    purpose     VARCHAR(30)  NOT NULL,
    token_hash  CHAR(64)     NOT NULL,
    payload     VARCHAR(255) NULL,
    expires_at  DATETIME     NOT NULL,
    used_at     DATETIME     NULL,
    created_at  DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    CONSTRAINT uq_user_tokens_hash UNIQUE (token_hash),
    CONSTRAINT fk_user_tokens_user FOREIGN KEY (user_id) REFERENCES users (id) ON DELETE CASCADE,
    INDEX ix_user_tokens_user_purpose (user_id, purpose),
    INDEX ix_user_tokens_expires_at (expires_at)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci;

CREATE TABLE revoked_tokens (
    jti         CHAR(36)  NOT NULL,
    user_id     BIGINT    NOT NULL,
    expires_at  DATETIME  NOT NULL,
    created_at  DATETIME  NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (jti),
    INDEX ix_revoked_tokens_expires_at (expires_at)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci;
