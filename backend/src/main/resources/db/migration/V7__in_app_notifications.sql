-- Phase 3: persistent in-app notifications (separate from email reminders). Empty table only.

CREATE TABLE notifications (
  notification_id BIGINT        NOT NULL AUTO_INCREMENT,
  user_id         BIGINT        NOT NULL,
  application_id  BIGINT        NULL,
  category        VARCHAR(20)   NOT NULL,
  dedup_key       VARCHAR(100)  NOT NULL,
  title           VARCHAR(300)  NOT NULL,
  body            VARCHAR(500)  NULL,
  href            VARCHAR(300)  NULL,
  read_at         DATETIME(6)   NULL,
  created_at      DATETIME(6)   NOT NULL,
  PRIMARY KEY (notification_id),
  CONSTRAINT fk_notif_user FOREIGN KEY (user_id) REFERENCES users (user_id) ON DELETE CASCADE,
  CONSTRAINT fk_notif_app  FOREIGN KEY (application_id) REFERENCES applications (application_id) ON DELETE SET NULL,
  CONSTRAINT uq_notif_dedup UNIQUE (user_id, dedup_key),
  CONSTRAINT ck_notif_cat CHECK (category IN ('DEADLINE','INTERVIEW','ASSESSMENT','FOLLOW_UP','SYSTEM')),
  INDEX idx_notif_user_created (user_id, created_at DESC),
  INDEX idx_notif_user_unread (user_id, read_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
