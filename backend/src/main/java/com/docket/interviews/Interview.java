package com.docket.interviews;

import jakarta.persistence.*;
import java.time.Instant;
import java.time.LocalDate;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Entity
@Table(name = "interviews")
public class Interview {
  @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
  @Column(name = "interview_id") Long id;
  @Column(name = "application_id", nullable = false, updatable = false) Long applicationId;
  @Column(name = "user_id", nullable = false, updatable = false) Long userId;
  @Column(name = "interview_date", nullable = false) Instant interviewDate;
  @Column(name = "round", nullable = false, length = 20) String round;
  @Column(name = "interview_mode", length = 20) String interviewMode;
  @Column(name = "meeting_link", length = 2048) String meetingLink;
  @JdbcTypeCode(SqlTypes.LONGVARCHAR) @Column(name = "preparation_notes", length = 5000) String preparationNotes;
  @JdbcTypeCode(SqlTypes.LONGVARCHAR) @Column(name = "topics_to_revise", length = 5000) String topicsToRevise;
  @JdbcTypeCode(SqlTypes.LONGVARCHAR) @Column(name = "questions_to_ask", length = 5000) String questionsToAsk;
  @Column(name = "result", nullable = false, length = 20) String result;
  @JdbcTypeCode(SqlTypes.LONGVARCHAR) @Column(name = "feedback", length = 5000) String feedback;
  @Column(name = "follow_up_date") LocalDate followUpDate;
  @Column(name = "created_at", nullable = false, updatable = false) Instant createdAt;
  @Column(name = "updated_at", nullable = false) Instant updatedAt;

  public Long getId() { return id; }
  public Long getApplicationId() { return applicationId; }
  public Instant getInterviewDate() { return interviewDate; }
  public String getRound() { return round; }
  public String getResult() { return result; }
  public LocalDate getFollowUpDate() { return followUpDate; }
  public Instant getCreatedAt() { return createdAt; }
  public String getInterviewMode() { return interviewMode; }
  public String getMeetingLink() { return meetingLink; }

  protected Interview() {}
  Interview(Long applicationId, Long userId) { this.applicationId = applicationId; this.userId = userId; }
  @PrePersist void onCreate() { createdAt = updatedAt = Instant.now(); }
  @PreUpdate void onUpdate() { updatedAt = Instant.now(); }
}
