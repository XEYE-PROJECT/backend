-- Sección F del backlog: endurecimiento del backend.
--
-- 1. Los embeddings salen de `trainings` (LONGTEXT de varios MB por run) a su propia tabla:
--    listar el historial ya no arrastra el blob; `has_embeddings` conserva el flag barato.
-- 2. `last_heartbeat_at`: cada callback del worker (incluido el latido de progreso repetido)
--    lo pone a NOW(). El barrido de estancados mira esta columna y no `updated_at`, que no
--    cambia cuando el latido llega con el mismo estado (Hibernate no emite el UPDATE).
-- 3. `version` para bloqueo optimista en trainings, lists y elements.
-- 4. Outbox transaccional: los eventos hacia el buscador y el training se escriben en la
--    misma transacción que el cambio y un relé los entrega con reintentos.
-- 5. Índices para el barrido, el cupo de lanzamientos, el modelo en uso y el historial de búsquedas.

CREATE TABLE training_embeddings (
    training_id     BIGINT   NOT NULL,
    embeddings_data LONGTEXT NOT NULL,
    created_at      DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (training_id),
    CONSTRAINT fk_training_embeddings_training FOREIGN KEY (training_id)
        REFERENCES trainings (id) ON DELETE CASCADE
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci;

INSERT INTO training_embeddings (training_id, embeddings_data)
SELECT id, embeddings_data FROM trainings WHERE embeddings_data IS NOT NULL;

ALTER TABLE trainings
    ADD COLUMN has_embeddings    BOOLEAN  NOT NULL DEFAULT FALSE AFTER model,
    ADD COLUMN last_heartbeat_at DATETIME NULL AFTER in_use,
    ADD COLUMN version           BIGINT   NOT NULL DEFAULT 0;

UPDATE trainings SET has_embeddings = TRUE WHERE embeddings_data IS NOT NULL;
UPDATE trainings SET last_heartbeat_at = updated_at WHERE status IN ('queued', 'initialized', 'optimizing', 'training');

ALTER TABLE trainings DROP COLUMN embeddings_data;

ALTER TABLE lists    ADD COLUMN version BIGINT NOT NULL DEFAULT 0;
ALTER TABLE elements ADD COLUMN version BIGINT NOT NULL DEFAULT 0;

CREATE INDEX ix_trainings_status_heartbeat ON trainings (status, last_heartbeat_at);
CREATE INDEX ix_trainings_list_in_use      ON trainings (list_id, in_use);
CREATE INDEX ix_trainings_user_status      ON trainings (user_id, status);
CREATE INDEX ix_searches_list_id_id        ON searches (list_id, id);
CREATE INDEX ix_searches_searched_at       ON searches (searched_at);

CREATE TABLE outbox_events (
    id           BIGINT       NOT NULL AUTO_INCREMENT,
    type         VARCHAR(60)  NOT NULL,
    payload      JSON         NOT NULL,
    status       VARCHAR(20)  NOT NULL DEFAULT 'pending',
    attempts     INT          NOT NULL DEFAULT 0,
    available_at DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    last_error   TEXT         NULL,
    created_at   DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    INDEX ix_outbox_status_available (status, available_at, id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci;
