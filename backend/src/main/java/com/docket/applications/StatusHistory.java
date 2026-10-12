package com.docket.applications;

import jakarta.persistence.*;
import java.time.Instant;

/** Append-only record of every status change (and of the initial status on creation). */
@Entity
@Table(name = "application_status_history")
public class StatusHistory {
  @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
  @Column(name = "history_id") Long id;
  @Column(name = "application_id", nullable = false, updatable = false) Long applicationId;
  @Column(name = "user_id", nullable = false, updatable = false) Long userId;
  @Column(name = "previous_status", length = 20, updatable = false) String previousStatus;
  @Column(name = "new_status", nullable = false, length = 20, updatable = false) String newStatus;
  @Column(name = "note", length = 500, updatable = false) String note;
  @Column(name = "changed_at", nullable = false, updatable = false) Instant changedAt;

  protected StatusHistory() {}
  public Long getApplicationId() { return applicationId; }
  public String getPreviousStatus() { return previousStatus; }
  public String getNewStatus() { return newStatus; }
  public Instant getChangedAt() { return changedAt; }
  StatusHistory(Long applicationId, Long userId, String previous, String next, String note, Instant at) {
    this.applicationId = applicationId; this.userId = userId; this.previousStatus = previous;
    this.newStatus = next; this.note = note; this.changedAt = at;
  }
}
