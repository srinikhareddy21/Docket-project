package com.docket.interviews;

import com.docket.applications.Application;
import com.docket.applications.ApplicationRepository;
import com.docket.reminders.ReminderService;
import com.docket.notifications.NotificationService;
import com.docket.calendar.CalendarService;
import com.docket.security.AuthUser;
import com.docket.web.ApiException;
import com.docket.web.Strings;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api")
public class InterviewController {
  private static final String NO_APP = "We could not find that application.";
  private static final String NO_INTERVIEW = "We could not find that interview.";
  private static final int MAX_PER_APPLICATION = 30;

  @JsonIgnoreProperties(ignoreUnknown = true)
  public record InterviewRequest(
      @NotNull(message = "Choose a date and time") Instant interviewDate,
      @Pattern(regexp = "Technical|HR|Managerial|Assessment|Other", message = "Choose a valid round") String round,
      @Pattern(regexp = "Video call|Phone|Onsite", message = "Choose a valid mode") String interviewMode,
      @Size(max = 2048, message = "This link is too long") String meetingLink,
      @Size(max = 5000, message = "Keep this under 5,000 characters") String preparationNotes,
      @Size(max = 5000, message = "Keep this under 5,000 characters") String topicsToRevise,
      @Size(max = 5000, message = "Keep this under 5,000 characters") String questionsToAsk,
      @Pattern(regexp = "Pending|Passed|Failed|Awaiting outcome", message = "Choose a valid outcome") String result,
      @Size(max = 5000, message = "Keep this under 5,000 characters") String feedback,
      LocalDate followUpDate) {}

  public record InterviewResponse(Long id, Long applicationId, String companyName, String position, Instant interviewDate,
      String round, String interviewMode, String meetingLink, String preparationNotes, String topicsToRevise,
      String questionsToAsk, String result, String feedback, LocalDate followUpDate) {}

  private final InterviewRepository repo;
  private final ApplicationRepository apps;
  private final ReminderService reminders;
  private final NotificationService notifications;
  private final CalendarService calendar;
  public InterviewController(InterviewRepository repo, ApplicationRepository apps, ReminderService reminders,
                             NotificationService notifications, CalendarService calendar) {
    this.repo = repo; this.apps = apps; this.reminders = reminders; this.notifications = notifications; this.calendar = calendar;
  }

  @GetMapping("/interviews") @Transactional(readOnly = true)
  public List<InterviewResponse> list(@AuthenticationPrincipal AuthUser user, @RequestParam(required = false) Long applicationId) {
    return repo.findWithApplication(user.userId(), applicationId == null ? 0L : applicationId).stream()
        .map(r -> toResponse((Interview) r[0], (Application) r[1])).toList();
  }

  @PostMapping("/applications/{appId}/interviews") @ResponseStatus(HttpStatus.CREATED) @Transactional
  public InterviewResponse create(@AuthenticationPrincipal AuthUser user, @PathVariable long appId, @Valid @RequestBody InterviewRequest body) {
    Application a = apps.findByIdAndUserIdAndDeletedAtIsNull(appId, user.userId()).orElseThrow(() -> ApiException.notFound(NO_APP));
    if (repo.findWithApplication(user.userId(), appId).size() >= MAX_PER_APPLICATION)
      throw ApiException.badRequest("This application already has the maximum of " + MAX_PER_APPLICATION + " interview rounds.", null);
    Interview i = new Interview(appId, user.userId());
    apply(i, body);
    InterviewResponse out = toResponse(repo.save(i), a);
    reminders.syncQuietly(user.userId()); notifications.syncQuietly(user.userId()); calendar.syncQuietly(user.userId());
    return out;
  }

  @PutMapping("/interviews/{id}") @Transactional
  public InterviewResponse update(@AuthenticationPrincipal AuthUser user, @PathVariable long id, @Valid @RequestBody InterviewRequest body) {
    Interview i = repo.findByIdAndUserId(id, user.userId()).orElseThrow(() -> ApiException.notFound(NO_INTERVIEW));
    Application a = apps.findByIdAndUserIdAndDeletedAtIsNull(i.applicationId, user.userId()).orElseThrow(() -> ApiException.notFound(NO_INTERVIEW));
    apply(i, body);
    InterviewResponse out = toResponse(repo.save(i), a);
    reminders.syncQuietly(user.userId()); notifications.syncQuietly(user.userId()); calendar.syncQuietly(user.userId());
    return out;
  }

  @DeleteMapping("/interviews/{id}") @ResponseStatus(HttpStatus.NO_CONTENT) @Transactional
  public void delete(@AuthenticationPrincipal AuthUser user, @PathVariable long id) {
    repo.delete(repo.findByIdAndUserId(id, user.userId()).orElseThrow(() -> ApiException.notFound(NO_INTERVIEW)));
    reminders.syncQuietly(user.userId()); notifications.syncQuietly(user.userId()); calendar.syncQuietly(user.userId());
  }

  private static void apply(Interview i, InterviewRequest r) {
    String link = Strings.blank(r.meetingLink());
    if (link != null && !Strings.validHttpUrl(link))
      throw ApiException.badRequest("Some details are invalid. Check the form and try again.", Map.of("meetingLink", "Enter a full link starting with http:// or https://"));
    i.interviewDate = r.interviewDate();
    i.round = r.round() == null ? "Technical" : r.round();
    i.interviewMode = Strings.blank(r.interviewMode());
    i.meetingLink = link;
    i.preparationNotes = Strings.blank(r.preparationNotes());
    i.topicsToRevise = Strings.blank(r.topicsToRevise());
    i.questionsToAsk = Strings.blank(r.questionsToAsk());
    i.result = r.result() == null ? "Pending" : r.result();
    i.feedback = Strings.blank(r.feedback());
    i.followUpDate = r.followUpDate();
  }

  private static InterviewResponse toResponse(Interview i, Application a) {
    return new InterviewResponse(i.id, i.applicationId, a.getCompanyName(), a.getPosition(), i.interviewDate, i.round, i.interviewMode,
        i.meetingLink, i.preparationNotes, i.topicsToRevise, i.questionsToAsk, i.result, i.feedback, i.followUpDate);
  }
}
