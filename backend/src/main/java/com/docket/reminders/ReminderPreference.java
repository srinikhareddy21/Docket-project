package com.docket.reminders;

import jakarta.persistence.*;
import java.time.Instant;

@Entity
@Table(name = "reminder_preferences")
public class ReminderPreference {
  @Id @Column(name = "user_id") Long userId;
  @Column(name = "email_enabled", nullable = false) boolean emailEnabled;
  @Column(name = "deadline_lead_hours", nullable = false) int deadlineLeadHours = 48;
  @Column(name = "interview_lead_hours", nullable = false) int interviewLeadHours = 24;
  @Column(name = "follow_up_enabled", nullable = false) boolean followUpEnabled = true;
  @Column(name = "time_zone", nullable = false, length = 64) String timeZone = "UTC";
  @Column(name = "updated_at", nullable = false) Instant updatedAt;

  protected ReminderPreference() {}
  public ReminderPreference(Long userId) { this.userId = userId; }
  @PrePersist @PreUpdate void touch() { updatedAt = Instant.now(); }

  public Long getUserId() { return userId; }
  public boolean isEmailEnabled() { return emailEnabled; }
  public int getDeadlineLeadHours() { return deadlineLeadHours; }
  public int getInterviewLeadHours() { return interviewLeadHours; }
  public boolean isFollowUpEnabled() { return followUpEnabled; }
  public String getTimeZone() { return timeZone; }
}

