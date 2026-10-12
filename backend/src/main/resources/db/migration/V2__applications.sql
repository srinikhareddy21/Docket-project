-- Phase 3: applications and status history. Empty tables only. No seed rows, ever.

CREATE TABLE applications (
  application_id     BIGINT        NOT NULL AUTO_INCREMENT,
  user_id            BIGINT        NOT NULL,
  company_name       VARCHAR(150)  NOT NULL,
  position           VARCHAR(150)  NOT NULL,
  job_type           VARCHAR(20)   NULL,
  work_mode          VARCHAR(20)   NULL,
  location           VARCHAR(150)  NULL,
  job_description    MEDIUMTEXT    NULL,
  application_source VARCHAR(40)   NULL,
  application_date   DATE          NULL,
  deadline           DATETIME(6)   NULL,
  application_link   VARCHAR(2048) NULL,
  salary_or_stipend  VARCHAR(100)  NULL,
  recruiter_details  VARCHAR(500)  NULL,
  notes              MEDIUMTEXT    NULL,
  current_status     VARCHAR(20)   NOT NULL,
  resume_version     VARCHAR(100)  NULL,
  follow_up_date     DATE          NULL,
  -- Normalized copies used only to find duplicates.
  company_key        VARCHAR(160)  NOT NULL,
  position_key       VARCHAR(160)  NOT NULL,
  link_key           VARCHAR(64)   NOT NULL DEFAULT '',
  status_changed_at  DATETIME(6)   NOT NULL,
  deleted_at         DATETIME(6)   NULL,
  created_at         DATETIME(6)   NOT NULL,
  updated_at         DATETIME(6)   NOT NULL,
  PRIMARY KEY (application_id),
  CONSTRAINT fk_app_user FOREIGN KEY (user_id) REFERENCES users (user_id) ON DELETE CASCADE,
  CONSTRAINT ck_app_status CHECK (current_status IN ('Applied','Shortlisted','Assessment','Interview','Selected','Rejected','Withdrawn')),
  INDEX idx_app_user_status   (user_id, deleted_at, current_status),
  INDEX idx_app_user_date     (user_id, deleted_at, application_date),
  INDEX idx_app_user_deadline (user_id, deleted_at, deadline),
  INDEX idx_app_user_followup (user_id, deleted_at, follow_up_date),
  INDEX idx_app_dup_pair      (user_id, company_key, position_key),
  INDEX idx_app_dup_link      (user_id, link_key),
  INDEX idx_app_deleted       (deleted_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- Required skills, one row per skill, kept in the order the user entered them.
CREATE TABLE application_skills (
  application_id BIGINT      NOT NULL,
  sort_order     INT         NOT NULL,
  skill_name     VARCHAR(80) NOT NULL,
  PRIMARY KEY (application_id, sort_order),
  CONSTRAINT fk_skill_app FOREIGN KEY (application_id) REFERENCES applications (application_id) ON DELETE CASCADE,
  INDEX idx_skill_name (skill_name)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE application_status_history (
  history_id      BIGINT       NOT NULL AUTO_INCREMENT,
  application_id  BIGINT       NOT NULL,
  user_id         BIGINT       NOT NULL,
  previous_status VARCHAR(20)  NULL,
  new_status      VARCHAR(20)  NOT NULL,
  note            VARCHAR(500) NULL,
  changed_at      DATETIME(6)  NOT NULL,
  PRIMARY KEY (history_id),
  CONSTRAINT fk_hist_app  FOREIGN KEY (application_id) REFERENCES applications (application_id) ON DELETE CASCADE,
  CONSTRAINT fk_hist_user FOREIGN KEY (user_id) REFERENCES users (user_id) ON DELETE CASCADE,
  INDEX idx_hist_app (application_id, changed_at),
  INDEX idx_hist_user (user_id, changed_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
