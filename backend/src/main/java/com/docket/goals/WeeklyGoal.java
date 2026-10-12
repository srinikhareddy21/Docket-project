package com.docket.goals;

import jakarta.persistence.*;
import java.time.Instant;

/** One standing weekly target per user. It applies to every week; progress is calculated, never stored. */
@Entity
@Table(name = "weekly_goals")
public class WeeklyGoal {
  @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
  @Column(name = "goal_id") Long id;
  @Column(name = "user_id", nullable = false, updatable = false) Long userId;
  @Column(name = "target", nullable = false) int target;
  @Column(name = "created_at", nullable = false, updatable = false) Instant createdAt;
  @Column(name = "updated_at", nullable = false) Instant updatedAt;

  protected WeeklyGoal() {}
  WeeklyGoal(Long userId, int target) { this.userId = userId; this.target = target; }
  @PrePersist void onCreate() { createdAt = updatedAt = Instant.now(); }
  @PreUpdate void onUpdate() { updatedAt = Instant.now(); }
}
