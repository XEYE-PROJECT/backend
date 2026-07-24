-- Search call log, written asynchronously by the search microservice through
-- POST /internal/search/logs. `list_id`/`api_key_id` are SET NULL on delete so the
-- history survives the referenced rows; `list_name` keeps the label readable.

CREATE TABLE searches (
    id            BIGINT       NOT NULL AUTO_INCREMENT,
    user_id       BIGINT       NOT NULL,
    api_key_id    BIGINT       NULL,
    list_id       BIGINT       NULL,
    list_name     VARCHAR(100) NOT NULL,
    endpoint      VARCHAR(20)  NOT NULL,
    search_term   TEXT         NOT NULL,
    total_results INT          NOT NULL DEFAULT 0,
    duration_ms   INT          NOT NULL DEFAULT 0,
    session       VARCHAR(255) NULL,
    results       JSON         NULL,
    searched_at   DATETIME     NOT NULL,
    created_at    DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    CONSTRAINT fk_searches_user FOREIGN KEY (user_id) REFERENCES users (id) ON DELETE CASCADE,
    CONSTRAINT fk_searches_api_key FOREIGN KEY (api_key_id) REFERENCES api_keys (id) ON DELETE SET NULL,
    CONSTRAINT fk_searches_list FOREIGN KEY (list_id) REFERENCES lists (id) ON DELETE SET NULL,
    INDEX ix_searches_user_id (user_id),
    INDEX ix_searches_list_id (list_id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci;
