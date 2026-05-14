-- ──────────────────────────────────────────────────────────────────────────────
-- V1 : Initial schema — users, refresh_tokens, oauth2_accounts
-- ──────────────────────────────────────────────────────────────────────────────

CREATE TABLE IF NOT EXISTS users (
    id            BINARY(16)   NOT NULL,
    email         VARCHAR(255) NOT NULL,
    password_hash VARCHAR(60)  NULL,
    full_name     VARCHAR(100) NOT NULL,
    role          VARCHAR(20)  NOT NULL DEFAULT 'STAFF',
    enabled       TINYINT(1)   NOT NULL DEFAULT 1,
    created_at    DATETIME(6)  NOT NULL,
    updated_at    DATETIME(6)  NOT NULL,
    CONSTRAINT pk_users      PRIMARY KEY (id),
    CONSTRAINT uq_users_email UNIQUE (email)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS refresh_tokens (
    id          BIGINT       NOT NULL AUTO_INCREMENT,
    user_id     BINARY(16)   NOT NULL,
    token_hash  VARCHAR(255) NOT NULL,
    expires_at  DATETIME(6)  NOT NULL,
    revoked     TINYINT(1)   NOT NULL DEFAULT 0,
    created_at  DATETIME(6)  NOT NULL,
    CONSTRAINT pk_refresh_tokens PRIMARY KEY (id),
    CONSTRAINT fk_rt_user FOREIGN KEY (user_id) REFERENCES users (id) ON DELETE CASCADE,
    INDEX idx_rt_user_revoked_expires (user_id, revoked, expires_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS oauth2_accounts (
    id          BIGINT       NOT NULL AUTO_INCREMENT,
    user_id     BINARY(16)   NOT NULL,
    provider    VARCHAR(30)  NOT NULL,
    provider_id VARCHAR(255) NOT NULL,
    email       VARCHAR(255) NOT NULL,
    picture_url VARCHAR(512) NULL,
    created_at  DATETIME(6)  NOT NULL,
    CONSTRAINT pk_oauth2_accounts          PRIMARY KEY (id),
    CONSTRAINT uq_oauth2_provider_id       UNIQUE (provider, provider_id),
    CONSTRAINT fk_oauth2_user              FOREIGN KEY (user_id) REFERENCES users (id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
