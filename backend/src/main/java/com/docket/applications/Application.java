package com.docket.applications;

import jakarta.persistence.*;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import org.hibernate.annotations.BatchSize;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/** One job or internship application. Always accessed through queries that filter by user_id. */
@Entity
@Table(name = "applications")
public class Application {
  @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
  @Column(name = "application_id") Long id;
  @Column(name = "user_id", nullable = false, updatable = false) Long userId;

  @Column(name = "company_name", nullable = false, length = 150) String companyName;
  @Column(name = "position", nullable = false, length = 150) String position;
  @Column(name = "job_type", length = 20) String jobType;
  @Column(name = "work_mode", length = 20) String workMode;
  @Column(name = "location", length = 150) String location;
  @JdbcTypeCode(SqlTypes.LONGVARCHAR) @Column(name = "job_description", length = 20000) String jobDescription;
  @Column(name = "application_source", length = 40) String applicationSource;
  @Column(name = "application_date") LocalDate applicationDate;
  @Column(name = "deadline") Instant deadline;
  @Column(name = "application_link", length = 2048) String applicationLink;
  @Column(name = "salary_or_stipend", length = 100) String salaryOrStipend;
  @Column(name = "recruiter_details", length = 500) String recruiterDetails;
  @JdbcTypeCode(SqlTypes.LONGVARCHAR) @Column(name = "notes", length = 10000) String notes;
  @Column(name = "current_status", nullable = false, length = 20) String currentStatus;
  @Column(name = "resume_version", length = 100) String resumeVersion;
  @Column(name = "follow_up_date") LocalDate followUpDate;

  /** Lower-cased, punctuation-free copies used only for duplicate detection. */
  @Column(name = "company_key", nullable = false, length = 160) String companyKey;
  @Column(name = "position_key", nullable = false, length = 160) String positionKey;
  /** SHA-256 of the normalized job URL, or empty when there is no usable link. */
  @Column(name = "link_key", nullable = false, length = 64) String linkKey;

  @Column(name = "status_changed_at", nullable = false) Instant statusChangedAt;
  @Column(name = "deleted_at") Instant deletedAt;
  @Column(name = "created_at", nullable = false, updatable = false) Instant createdAt;
  @Column(name = "updated_at", nullable = false) Instant updatedAt;

  @ElementCollection(fetch = FetchType.LAZY)
  @CollectionTable(name = "application_skills", joinColumns = @JoinColumn(name = "application_id"))
  @Column(name = "skill_name", nullable = false, length = 80)
  @OrderColumn(name = "sort_order")
  @BatchSize(size = 100)
  List<String> requiredSkills = new ArrayList<>();

  protected Application() {}
  Application(Long userId) { this.userId = userId; }

  @PrePersist void onCreate() { createdAt = updatedAt = Instant.now(); if (statusChangedAt == null) statusChangedAt = createdAt; }
  @PreUpdate void onUpdate() { updatedAt = Instant.now(); }

  public Long getId() { return id; }
  public Long getUserId() { return userId; }
  public String getCompanyName() { return companyName; }
  public String getPosition() { return position; }
  public String getCurrentStatus() { return currentStatus; }
  public String getApplicationSource() { return applicationSource; }
  public java.time.LocalDate getApplicationDate() { return applicationDate; }
  public Instant getDeadline() { return deadline; }
  public java.time.LocalDate getFollowUpDate() { return followUpDate; }
  public Instant getStatusChangedAt() { return statusChangedAt; }
  public List<String> getRequiredSkills() { return requiredSkills; }
}
