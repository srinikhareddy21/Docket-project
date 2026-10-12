-- Phase 4: personal skills, interviews, checklists, document metadata. Empty tables only. No seed rows, ever.

CREATE TABLE user_skills (
  skill_id   BIGINT       NOT NULL AUTO_INCREMENT,
  user_id    BIGINT       NOT NULL,
  name       VARCHAR(80)  NOT NULL,
  name_key   VARCHAR(80)  NOT NULL,
  category   VARCHAR(40)  NOT NULL,
  created_at DATETIME(6)  NOT NULL,
  PRIMARY KEY (skill_id),
  CONSTRAINT fk_skill_user FOREIGN KEY (user_id) REFERENCES users (user_id) ON DELETE CASCADE,
  CONSTRAINT uq_user_skill UNIQUE (user_id, name_key)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE interviews (
  interview_id      BIGINT        NOT NULL AUTO_INCREMENT,
  application_id    BIGINT        NOT NULL,
  user_id           BIGINT        NOT NULL,
  interview_date    DATETIME(6)   NOT NULL,
  round             VARCHAR(20)   NOT NULL,
  interview_mode    VARCHAR(20)   NULL,
  meeting_link      VARCHAR(2048) NULL,
  preparation_notes TEXT          NULL,
  topics_to_revise  TEXT          NULL,
  questions_to_ask  TEXT          NULL,
  result            VARCHAR(20)   NOT NULL,
  feedback          TEXT          NULL,
  follow_up_date    DATE          NULL,
  created_at        DATETIME(6)   NOT NULL,
  updated_at        DATETIME(6)   NOT NULL,
  PRIMARY KEY (interview_id),
  CONSTRAINT fk_int_app  FOREIGN KEY (application_id) REFERENCES applications (application_id) ON DELETE CASCADE,
  CONSTRAINT fk_int_user FOREIGN KEY (user_id) REFERENCES users (user_id) ON DELETE CASCADE,
  CONSTRAINT ck_int_result CHECK (result IN ('Pending','Passed','Failed','Awaiting outcome')),
  INDEX idx_int_user_date (user_id, interview_date),
  INDEX idx_int_app (application_id),
  INDEX idx_int_followup (user_id, follow_up_date)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- One table, two separate lists told apart by type: PREP (interview preparation) and REQUIREMENT (job requirements).
CREATE TABLE checklist_items (
  item_id        BIGINT       NOT NULL AUTO_INCREMENT,
  application_id BIGINT       NOT NULL,
  user_id        BIGINT       NOT NULL,
  interview_id   BIGINT       NULL,
  type           VARCHAR(12)  NOT NULL,
  category       VARCHAR(40)  NULL,
  text           VARCHAR(300) NOT NULL,
  completed      BOOLEAN      NOT NULL DEFAULT FALSE,
  created_at     DATETIME(6)  NOT NULL,
  updated_at     DATETIME(6)  NOT NULL,
  PRIMARY KEY (item_id),
  CONSTRAINT fk_chk_app  FOREIGN KEY (application_id) REFERENCES applications (application_id) ON DELETE CASCADE,
  CONSTRAINT fk_chk_user FOREIGN KEY (user_id) REFERENCES users (user_id) ON DELETE CASCADE,
  CONSTRAINT fk_chk_int  FOREIGN KEY (interview_id) REFERENCES interviews (interview_id) ON DELETE SET NULL,
  CONSTRAINT ck_chk_type CHECK (type IN ('PREP','REQUIREMENT')),
  INDEX idx_chk_app (application_id, type),
  INDEX idx_chk_user_prep (user_id, type, completed)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- Metadata only. The file itself is on disk under storage_key (a random name) in the server's storage directory.
CREATE TABLE documents (
  document_id    BIGINT       NOT NULL AUTO_INCREMENT,
  application_id BIGINT       NOT NULL,
  user_id        BIGINT       NOT NULL,
  name           VARCHAR(150) NOT NULL,
  doc_type       VARCHAR(20)  NOT NULL,
  content_type   VARCHAR(100) NOT NULL,
  size_bytes     BIGINT       NOT NULL,
  storage_key    VARCHAR(64)  NOT NULL,
  uploaded_at    DATETIME(6)  NOT NULL,
  PRIMARY KEY (document_id),
  CONSTRAINT fk_doc_app  FOREIGN KEY (application_id) REFERENCES applications (application_id) ON DELETE CASCADE,
  CONSTRAINT fk_doc_user FOREIGN KEY (user_id) REFERENCES users (user_id) ON DELETE CASCADE,
  CONSTRAINT uq_doc_key UNIQUE (storage_key),
  INDEX idx_doc_app (application_id, uploaded_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
