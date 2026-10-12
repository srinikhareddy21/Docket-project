package com.docket.applications;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

/** Explicit request and response shapes. Clients can only set the fields listed here (no mass assignment). */
public final class ApplicationDtos {
  private ApplicationDtos() {}

  @JsonIgnoreProperties(ignoreUnknown = true)
  public record ApplicationRequest(
      @NotBlank(message = "Enter the company name") @Size(max = 150, message = "Keep this under 150 characters") String companyName,
      @NotBlank(message = "Enter the job title") @Size(max = 150, message = "Keep this under 150 characters") String position,
      @Pattern(regexp = Vocab.JOB_TYPE_REGEX, message = "Choose a valid job type") String jobType,
      @Pattern(regexp = Vocab.WORK_MODE_REGEX, message = "Choose a valid work mode") String workMode,
      @Size(max = 150, message = "Keep this under 150 characters") String location,
      @Size(max = 20000, message = "Keep the description under 20,000 characters") String jobDescription,
      @Size(max = 50, message = "Add at most 50 skills") List<@Size(max = 80, message = "Each skill must be under 80 characters") String> requiredSkills,
      @Pattern(regexp = Vocab.SOURCE_REGEX, message = "Choose a valid source") String applicationSource,
      LocalDate applicationDate,
      Instant deadline,
      @Size(max = 2048, message = "This link is too long") String applicationLink,
      @Size(max = 100, message = "Keep this under 100 characters") String salaryOrStipend,
      @Size(max = 500, message = "Keep this under 500 characters") String recruiterDetails,
      @Size(max = 10000, message = "Keep notes under 10,000 characters") String notes,
      @Pattern(regexp = Vocab.STATUS_REGEX, message = "Choose a valid status") String currentStatus,
      @Size(max = 100, message = "Keep this under 100 characters") String resumeVersion,
      LocalDate followUpDate) {}

  @JsonIgnoreProperties(ignoreUnknown = true)
  public record StatusRequest(
      @NotBlank(message = "Choose a status") @Pattern(regexp = Vocab.STATUS_REGEX, message = "Choose a valid status") String status,
      @Size(max = 500, message = "Keep the note under 500 characters") String note) {}

  @JsonIgnoreProperties(ignoreUnknown = true)
  public record BulkStatusRequest(
      @NotEmpty(message = "Select at least one application") @Size(max = 200, message = "Select at most 200 applications at a time") List<Long> ids,
      @NotBlank(message = "Choose a status") @Pattern(regexp = Vocab.STATUS_REGEX, message = "Choose a valid status") String status,
      @Size(max = 500, message = "Keep the note under 500 characters") String note) {}

  public record PriorityDto(int score, String level, List<String> reasons) {}

  public record ApplicationResponse(
      Long id, String companyName, String position, String jobType, String workMode, String location,
      String jobDescription, List<String> requiredSkills, String applicationSource, LocalDate applicationDate,
      Instant deadline, String applicationLink, String salaryOrStipend, String recruiterDetails, String notes,
      String currentStatus, String resumeVersion, LocalDate followUpDate,
      /** Null until personal skills exist (Phase 4). The server never invents a score. */
      Double matchScore, PriorityDto priority, String nextAction, Instant createdAt, Instant updatedAt) {}

  public record PagedResponse<T>(List<T> content, long totalElements, int totalPages, int page, int size) {}

  public record DuplicateMatch(Long id, String companyName, String position, String currentStatus) {}

  public record HistoryEntry(Long id, String previousStatus, String newStatus, Instant changedAt, String note) {}

  public record Suggestions(List<String> companies, List<String> roles) {}

  public record BulkResult(int updated) {}
}
