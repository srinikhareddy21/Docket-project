package com.docket.skills;

import jakarta.persistence.*;
import java.time.Instant;

@Entity
@Table(name = "user_skills")
public class Skill {
  @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
  @Column(name = "skill_id") Long id;
  @Column(name = "user_id", nullable = false, updatable = false) Long userId;
  @Column(name = "name", nullable = false, length = 80) String name;
  /** Normalized (case, spacing, aliases) so "JS" and "JavaScript" count as one skill. */
  @Column(name = "name_key", nullable = false, length = 80) String nameKey;
  @Column(name = "category", nullable = false, length = 40) String category;
  @Column(name = "created_at", nullable = false, updatable = false) Instant createdAt;

  protected Skill() {}
  Skill(Long userId, String name, String nameKey, String category) { this.userId = userId; this.name = name; this.nameKey = nameKey; this.category = category; }
  @PrePersist void onCreate() { createdAt = Instant.now(); }
}
