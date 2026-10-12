package com.docket.checklist;

import jakarta.persistence.*;
import java.time.Instant;

/** One table, two separate lists: type PREP (interview preparation) and REQUIREMENT (job requirements). */
@Entity
@Table(name = "checklist_items")
public class ChecklistItem {
  @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
  @Column(name = "item_id") Long id;
  @Column(name = "application_id", nullable = false, updatable = false) Long applicationId;
  @Column(name = "user_id", nullable = false, updatable = false) Long userId;
  /** Optional: ties a PREP item to one interview round of the same application. */
  @Column(name = "interview_id") Long interviewId;
  @Column(name = "type", nullable = false, updatable = false, length = 12) String type;
  @Column(name = "category", length = 40) String category;
  @Column(name = "text", nullable = false, length = 300) String text;
  @Column(name = "completed", nullable = false) boolean completed;
  @Column(name = "created_at", nullable = false, updatable = false) Instant createdAt;
  @Column(name = "updated_at", nullable = false) Instant updatedAt;

  protected ChecklistItem() {}
  public Long getApplicationId() { return applicationId; }
  public String getText() { return text; }
  public Instant getUpdatedAt() { return updatedAt; }
  ChecklistItem(Long applicationId, Long userId, String type) { this.applicationId = applicationId; this.userId = userId; this.type = type; }
  @PrePersist void onCreate() { createdAt = updatedAt = Instant.now(); }
  @PreUpdate void onUpdate() { updatedAt = Instant.now(); }
}
