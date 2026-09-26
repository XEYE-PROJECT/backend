-- API keys dejan de guardarse en claro: solo su SHA-256 (hex, minúsculas) y un prefijo
-- identificativo. Las claves existentes siguen funcionando: se hashean aquí con SHA2(), que
-- produce exactamente lo mismo que ApiKeyHasher (Java) y hash_api_key (search-service).
-- Irreversible: la columna en claro desaparece. Hacer backup antes de aplicar en producción.
ALTER TABLE api_keys
    ADD COLUMN key_hash   CHAR(64)    NULL AFTER name,
    ADD COLUMN key_prefix VARCHAR(12) NOT NULL DEFAULT '' AFTER key_hash;

UPDATE api_keys
SET key_hash   = SHA2(api_key, 256),
    key_prefix = LEFT(api_key, 12);

ALTER TABLE api_keys
    MODIFY COLUMN key_hash CHAR(64) NOT NULL,
    ALTER COLUMN key_prefix DROP DEFAULT,
    DROP INDEX uq_api_keys_api_key,
    DROP COLUMN api_key,
    ADD CONSTRAINT uq_api_keys_key_hash UNIQUE (key_hash);
