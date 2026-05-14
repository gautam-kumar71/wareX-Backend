CREATE TABLE IF NOT EXISTS password_reset_otps (
    id          BIGINT       NOT NULL AUTO_INCREMENT,
    email       VARCHAR(255) NOT NULL,
    otp_hash    VARCHAR(64)  NOT NULL,
    expires_at  DATETIME(6)  NOT NULL,
    consumed_at DATETIME(6)  NULL,
    created_at  DATETIME(6)  NOT NULL,
    CONSTRAINT pk_password_reset_otps PRIMARY KEY (id),
    INDEX idx_password_reset_email_created (email, created_at),
    INDEX idx_password_reset_email_consumed (email, consumed_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
