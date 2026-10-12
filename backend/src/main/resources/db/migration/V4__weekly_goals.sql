-- Phase 5: optional weekly application target. One standing target per user (it applies to every week). Empty table only.

CREATE TABLE weekly_goals (
  goal_id    BIGINT      NOT NULL AUTO_INCREMENT,
  user_id    BIGINT      NOT NULL,
  target     INT         NOT NULL,
  created_at DATETIME(6) NOT NULL,
  updated_at DATETIME(6) NOT NULL,
  PRIMARY KEY (goal_id),
  CONSTRAINT fk_goal_user FOREIGN KEY (user_id) REFERENCES users (user_id) ON DELETE CASCADE,
  CONSTRAINT uq_goal_user UNIQUE (user_id),
  CONSTRAINT ck_goal_target CHECK (target BETWEEN 1 AND 200)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
