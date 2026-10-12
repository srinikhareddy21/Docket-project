-- Phase 2: accounts and token lifecycle only. No seed rows, ever.
-- Application, interview, skill and other tables arrive in later migrations.

CREATE TABLE users (
  user_id           BIGINT       NOT NULL AUTO_INCREMENT,
  name              VARCHAR(120) NOT NULL,
  email             VARCHAR(254) NOT NULL,
  password_hash     VARCHAR(100) NOT NULL,
  email_verified    BOOLEAN      NOT NULL DEFAULT FALSE,
  terms_accepted_at DATETIME(6)  NOT NULL,
  created_at        DATETIME(6)  NOT NULL,
  updated_at        DATETIME(6)  NOT NULL,
  PRIMARY KEY (user_id),
  CONSTRAINT uq_users_email UNIQUE (email)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- One row per issued refresh token. Rotation keeps the same family_id.
-- Only a SHA-256 hash of the token is stored.
CREATE TABLE refresh_tokens (
  token_id    BIGINT      NOT NULL AUTO_INCREMENT,
  user_id     BIGINT      NOT NULL,
  family_id   VARCHAR(36) NOT NULL,
  token_hash  VARCHAR(64) NOT NULL,
  remember    BOOLEAN     NOT NULL,
  expires_at  DATETIME(6) NOT NULL,
  rotated_at  DATETIME(6) NULL,
  revoked_at  DATETIME(6) NULL,
  created_at  DATETIME(6) NOT NULL,
  PRIMARY KEY (token_id),
  CONSTRAINT uq_refresh_token_hash UNIQUE (token_hash),
  CONSTRAINT fk_refresh_user FOREIGN KEY (user_id) REFERENCES users (user_id) ON DELETE CASCADE,
  INDEX idx_refresh_family (family_id),
  INDEX idx_refresh_user (user_id),
  INDEX idx_refresh_expires (expires_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE password_reset_tokens (
  token_id    BIGINT      NOT NULL AUTO_INCREMENT,
  user_id     BIGINT      NOT NULL,
  token_hash  VARCHAR(64) NOT NULL,
  expires_at  DATETIME(6) NOT NULL,
  used_at     DATETIME(6) NULL,
  created_at  DATETIME(6) NOT NULL,
  PRIMARY KEY (token_id),
  CONSTRAINT uq_reset_token_hash UNIQUE (token_hash),
  CONSTRAINT fk_reset_user FOREIGN KEY (user_id) REFERENCES users (user_id) ON DELETE CASCADE,
  INDEX idx_reset_user (user_id),
  INDEX idx_reset_expires (expires_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
