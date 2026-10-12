-- Phase 7: email-change verification, browser-extension tokens, Google Calendar connection and sync records.
-- Empty tables only. No seed rows, ever.

-- A pending change of login email. Only a SHA-256 of the emailed token is stored.
CREATE TABLE email_change_tokens (
  token_id   BIGINT       NOT NULL AUTO_INCREMENT,
  user_id    BIGINT       NOT NULL,
  new_email  VARCHAR(254) NOT NULL,
  token_hash VARCHAR(64) NOT NULL,
  expires_at DATETIME(6)  NOT NULL,
  used_at    DATETIME(6)  NULL,
  created_at DATETIME(6)  NOT NULL,
  PRIMARY KEY (token_id),
  CONSTRAINT uq_ect_hash UNIQUE (token_hash),
  CONSTRAINT fk_ect_user FOREIGN KEY (user_id) REFERENCES users (user_id) ON DELETE CASCADE,
  INDEX idx_ect_user (user_id),
  INDEX idx_ect_expires (expires_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- Personal access tokens for the Chrome extension. They can only call /api/extension/*.
-- Only a SHA-256 of the token is stored; the token itself is shown once, at creation. hint = last 4 characters.
CREATE TABLE extension_tokens (
  token_id     BIGINT      NOT NULL AUTO_INCREMENT,
  user_id      BIGINT      NOT NULL,
  label        VARCHAR(60) NOT NULL,
  token_hash   VARCHAR(64) NOT NULL,
  hint         VARCHAR(8)  NOT NULL,
  created_at   DATETIME(6) NOT NULL,
  expires_at   DATETIME(6) NOT NULL,
  last_used_at DATETIME(6) NULL,
  revoked_at   DATETIME(6) NULL,
  PRIMARY KEY (token_id),
  CONSTRAINT uq_ext_token UNIQUE (token_hash),
  CONSTRAINT fk_ext_user FOREIGN KEY (user_id) REFERENCES users (user_id) ON DELETE CASCADE,
  INDEX idx_ext_user (user_id, created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- One-time OAuth "state" values binding a Google consent screen to the user who started it.
CREATE TABLE oauth_states (
  state_id   BIGINT      NOT NULL AUTO_INCREMENT,
  user_id    BIGINT      NOT NULL,
  state_hash VARCHAR(64) NOT NULL,
  expires_at DATETIME(6) NOT NULL,
  created_at DATETIME(6) NOT NULL,
  PRIMARY KEY (state_id),
  CONSTRAINT uq_oauth_state UNIQUE (state_hash),
  CONSTRAINT fk_oauth_user FOREIGN KEY (user_id) REFERENCES users (user_id) ON DELETE CASCADE,
  INDEX idx_oauth_expires (expires_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- The Google refresh token is stored encrypted (AES-256-GCM, key from TOKEN_ENCRYPTION_KEY), never in plain text.
CREATE TABLE google_calendar_connections (
  user_id            BIGINT       NOT NULL,
  refresh_token_enc  VARCHAR(1024) NOT NULL,
  scope              VARCHAR(500) NOT NULL,
  status             VARCHAR(16)  NOT NULL,
  sync_interviews    BOOLEAN      NOT NULL DEFAULT TRUE,
  sync_follow_ups    BOOLEAN      NOT NULL DEFAULT TRUE,
  sync_deadlines     BOOLEAN      NOT NULL DEFAULT TRUE,
  connected_at       DATETIME(6)  NOT NULL,
  last_sync_at       DATETIME(6)  NULL,
  last_sync_status   VARCHAR(10)  NULL,
  last_sync_note     VARCHAR(300) NULL,
  PRIMARY KEY (user_id),
  CONSTRAINT fk_gcc_user FOREIGN KEY (user_id) REFERENCES users (user_id) ON DELETE CASCADE,
  CONSTRAINT ck_gcc_status CHECK (status IN ('CONNECTED','NEEDS_RECONNECT')),
  CONSTRAINT ck_gcc_sync CHECK (last_sync_status IS NULL OR last_sync_status IN ('OK','PARTIAL','FAILED'))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- Which DOCKET item maps to which Google event. content_hash lets a sync skip unchanged events.
-- source_key examples: INTERVIEW:12, DEADLINE:5, FOLLOWUP_APP:5, FOLLOWUP_INT:12
CREATE TABLE calendar_events (
  row_id          BIGINT      NOT NULL AUTO_INCREMENT,
  user_id         BIGINT      NOT NULL,
  source_key      VARCHAR(40) NOT NULL,
  google_event_id VARCHAR(64) NOT NULL,
  content_hash    VARCHAR(64) NOT NULL,
  synced_at       DATETIME(6) NOT NULL,
  PRIMARY KEY (row_id),
  CONSTRAINT uq_cal_source UNIQUE (user_id, source_key),
  CONSTRAINT fk_cal_user FOREIGN KEY (user_id) REFERENCES users (user_id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
