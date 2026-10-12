package com.docket.reminders;

import jakarta.persistence.*;
import java.time.Instant;

/** One reminder and its delivery state. Status: PENDING, SENT (mail server accepted it), FAILED, SKIPPED, DISMISSED. */
@Entity
@Table(name = "reminders")
public class Reminder {
  @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
  @Column(name = "reminder_id") Long id;
  @Column(name = "user_id", nullable = false, updatable = false) Long userId;
  @Column(name = "application_id") Long applicationId;
  @Column(name = "reminder_type", nullable = false, updatable = false, length = 12) String type;
  @Column(name = "dedup_key", nullable = false, updatable = false, length = 80) String dedupKey;
  @Column(name = "title", nullable = false, length = 300) String title;
  @Column(name = "event_at", nullable = false) Instant eventAt;
  @Column(name = "remind_at", nullable = false) Instant remindAt;
  @Column(name = "status", nullable = false, length = 10) String status;
  @Column(name = "attempts", nullable = false) int attempts;
  @Column(name = "last_attempt_at") Instant lastAttemptAt;
  @Column(name = "sent_at") Instant sentAt;
  @Column(name = "note", length = 200) String note;
  @Column(name = "created_at", nullable = false, updatable = false) Instant createdAt;

  protected Reminder() {}
  Reminder(Long userId, Long applicationId, String type, String dedupKey, String title, Instant eventAt, Instant remindAt) {
    this.userId = userId; this.applicationId = applicationId; this.type = type; this.dedupKey = dedupKey;
    this.title = title; this.eventAt = eventAt; this.remindAt = remindAt; this.status = "PENDING";
  }
  @PrePersist void onCreate() { createdAt = Instant.now(); }

  public Long getId() { return id; }
  public Long getUserId() { return userId; }
  public String getTitle() { return title; }
  public Instant getEventAt() { return eventAt; }
}
