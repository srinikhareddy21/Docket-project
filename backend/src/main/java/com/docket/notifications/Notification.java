package com.docket.notifications;

import jakarta.persistence.*;
import java.time.Instant;

@Entity
@Table(name = "notifications")
public class Notification {
  @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
  @Column(name = "notification_id") Long id;
  @Column(name = "user_id", nullable = false, updatable = false) Long userId;
  @Column(name = "application_id") Long applicationId;
  @Column(name = "category", nullable = false, length = 20) String category;
  @Column(name = "dedup_key", nullable = false, updatable = false, length = 100) String dedupKey;
  @Column(name = "title", nullable = false, length = 300) String title;
  @Column(name = "body", length = 500) String body;
  @Column(name = "href", length = 300) String href;
  @Column(name = "read_at") Instant readAt;
  @Column(name = "created_at", nullable = false, updatable = false) Instant createdAt;

  protected Notification() {}
  Notification(Long userId, Long applicationId, String category, String dedupKey, String title, String body, String href) {
    this.userId = userId; this.applicationId = applicationId; this.category = category;
    this.dedupKey = dedupKey; this.title = title; this.body = body; this.href = href;
  }
  @PrePersist void onCreate() { if (createdAt == null) createdAt = Instant.now(); }
}
