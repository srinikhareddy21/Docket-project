-- Phase 6: reminder preferences, reminders (with delivery tracking) and share links. Empty tables only. No seed rows, ever.

CREATE TABLE reminder_preferences (
  user_id             BIGINT      NOT NULL,
  email_enabled       BOOLEAN     NOT NULL DEFAULT FALSE,
  deadline_lead_hours INT         NOT NULL DEFAULT 48,
  interview_lead_hours INT        NOT NULL DEFAULT 24,
  follow_up_enabled   BOOLEAN     NOT NULL DEFAULT TRUE,
  time_zone           VARCHAR(64) NOT NULL DEFAULT 'UTC',
  updated_at          DATETIME(6) NOT NULL,
  PRIMARY KEY (user_id),
  CONSTRAINT fk_rp_user FOREIGN KEY (user_id) REFERENCES users (user_id) ON DELETE CASCADE,
  CONSTRAINT ck_rp_deadline CHECK (deadline_lead_hours BETWEEN 1 AND 336),
  CONSTRAINT ck_rp_interview CHECK (interview_lead_hours BETWEEN 1 AND 336)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- One row per reminder. dedup_key makes generation idempotent: the same event never produces a second row,
-- and a changed deadline or interview time produces a new key (so a new reminder) while the old row is skipped.
CREATE TABLE reminders (
  reminder_id     BIGINT        NOT NULL AUTO_INCREMENT,
  user_id         BIGINT        NOT NULL,
  application_id  BIGINT        NULL,
  reminder_type   VARCHAR(12)   NOT NULL,
  dedup_key       VARCHAR(80)   NOT NULL,
  title           VARCHAR(300)  NOT NULL,
  event_at        DATETIME(6)   NOT NULL,
  remind_at       DATETIME(6)   NOT NULL,
  status          VARCHAR(10)   NOT NULL,
  attempts        INT           NOT NULL DEFAULT 0,
  last_attempt_at DATETIME(6)   NULL,
  sent_at         DATETIME(6)   NULL,
  note            VARCHAR(200)  NULL,
  created_at      DATETIME(6)   NOT NULL,
  PRIMARY KEY (reminder_id),
  CONSTRAINT fk_rem_user FOREIGN KEY (user_id) REFERENCES users (user_id) ON DELETE CASCADE,
  CONSTRAINT fk_rem_app  FOREIGN KEY (application_id) REFERENCES applications (application_id) ON DELETE CASCADE,
  CONSTRAINT uq_rem_dedup UNIQUE (user_id, dedup_key),
  CONSTRAINT ck_rem_type CHECK (reminder_type IN ('DEADLINE','INTERVIEW','FOLLOW_UP','CUSTOM')),
  CONSTRAINT ck_rem_status CHECK (status IN ('PENDING','SENT','FAILED','SKIPPED','DISMISSED')),
  INDEX idx_rem_user_event (user_id, event_at),
  INDEX idx_rem_due (status, remind_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- Only a SHA-256 of the share token is stored, so the link cannot be recovered from the database. It is shown once, at creation.
CREATE TABLE share_links (
  share_id   BIGINT      NOT NULL AUTO_INCREMENT,
  user_id    BIGINT      NOT NULL,
  token_hash VARCHAR(64) NOT NULL,
  created_at DATETIME(6) NOT NULL,
  expires_at DATETIME(6) NOT NULL,
  revoked_at DATETIME(6) NULL,
  PRIMARY KEY (share_id),
  CONSTRAINT fk_share_user FOREIGN KEY (user_id) REFERENCES users (user_id) ON DELETE CASCADE,
  CONSTRAINT uq_share_token UNIQUE (token_hash),
  INDEX idx_share_user (user_id, created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
