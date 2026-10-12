package com.docket.reminders;

import com.docket.applications.Application;
import com.docket.applications.ApplicationRepository;
import com.docket.applications.Vocab;
import com.docket.interviews.Interview;
import com.docket.interviews.InterviewRepository;
import com.docket.mail.MailService;
import com.docket.reminders.ReminderPlanner.*;
import com.docket.user.User;
import com.docket.user.UserRepository;
import com.docket.web.ApiException;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.context.annotation.Lazy;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

@Service
public class ReminderService {
  static final int MAX_ATTEMPTS = 3;
  static final int MAX_CUSTOM = 200;

  private final ReminderRepository reminders;
  private final ReminderPreferenceRepository prefsRepo;
  private final ApplicationRepository apps;
  private final InterviewRepository interviews;
  private final UserRepository users;
  private final MailService mail;

  private final ReminderService self;

  public ReminderService(ReminderRepository reminders, ReminderPreferenceRepository prefsRepo, ApplicationRepository apps,
                         InterviewRepository interviews, UserRepository users, MailService mail,
                         @Lazy ReminderService self) {
    this.reminders = reminders; this.prefsRepo = prefsRepo; this.apps = apps; this.interviews = interviews; this.users = users; this.mail = mail;
    this.self = self;
  }

  // ---------- preferences ----------

  public record PrefsDto(boolean emailEnabled, int deadlineLeadHours, int interviewLeadHours, boolean followUpEnabled, String timezone) {}

  @Transactional(readOnly = true)
  public PrefsDto prefs(long userId) { return dto(prefsRepo.findById(userId).orElseGet(() -> new ReminderPreference(userId))); }

  @Transactional
  public PrefsDto savePrefs(long userId, PrefsDto in) {
    try { ZoneId.of(in.timezone()); } catch (Exception e) {
      throw ApiException.badRequest("Choose a valid time zone.", Map.of("timezone", "Use a name like Asia/Kolkata or UTC"));
    }
    if (in.deadlineLeadHours() < 1 || in.deadlineLeadHours() > 336 || in.interviewLeadHours() < 1 || in.interviewLeadHours() > 336)
      throw ApiException.badRequest("Lead times must be between 1 and 336 hours.", null);
    ReminderPreference p = prefsRepo.findById(userId).orElseGet(() -> new ReminderPreference(userId));
    p.emailEnabled = in.emailEnabled();
    p.deadlineLeadHours = in.deadlineLeadHours();
    p.interviewLeadHours = in.interviewLeadHours();
    p.followUpEnabled = in.followUpEnabled();
    p.timeZone = in.timezone();
    return dto(prefsRepo.save(p));
  }

  private static PrefsDto dto(ReminderPreference p) {
    return new PrefsDto(p.emailEnabled, p.deadlineLeadHours, p.interviewLeadHours, p.followUpEnabled, p.timeZone);
  }

  private static Prefs planPrefs(ReminderPreference p) {
    ZoneId zone;
    try { zone = ZoneId.of(p.timeZone); } catch (Exception e) { zone = ZoneId.of("UTC"); }
    return new Prefs(p.emailEnabled, p.deadlineLeadHours, p.interviewLeadHours, p.followUpEnabled, zone);
  }

  // ---------- list / create / update ----------

  public record ReminderDto(long id, Long applicationId, String title, String type, Instant scheduledAt, Instant eventAt,
                            String deliveryStatus, String note, Instant sentAt) {}

  /** Brings the user's reminders up to date with their records, then lists the visible ones (events from 7 days ago on). */
  @Transactional
  public List<ReminderDto> list(long userId) {
    Instant now = Instant.now();
    sync(userId, now);
    return reminders.visible(userId, now.minus(Duration.ofDays(7)), PageRequest.of(0, 200)).stream().map(ReminderService::toDto).toList();
  }

  public record CreateRequest(Long applicationId, String title, Instant scheduledAt) {}

  @Transactional
  public ReminderDto createCustom(long userId, CreateRequest in) {
    String title = com.docket.web.Strings.blank(in.title());
    if (title == null || title.length() > 200) throw ApiException.badRequest("Enter a title of up to 200 characters.", Map.of("title", "Enter a title of up to 200 characters"));
    if (in.scheduledAt() == null || !in.scheduledAt().isAfter(Instant.now()))
      throw ApiException.badRequest("Choose a time in the future.", Map.of("scheduledAt", "Choose a time in the future"));
    if (reminders.countByUserIdAndType(userId, "CUSTOM") >= MAX_CUSTOM) throw ApiException.conflict("You have reached the limit of custom reminders.", null);
    if (in.applicationId() != null) apps.findByIdAndUserIdAndDeletedAtIsNull(in.applicationId(), userId)
        .orElseThrow(() -> ApiException.notFound("We could not find that application."));
    Reminder r = new Reminder(userId, in.applicationId(), "CUSTOM", "CUSTOM:" + UUID.randomUUID(), title, in.scheduledAt(), in.scheduledAt());
    return toDto(reminders.save(r));
  }

  public record UpdateRequest(String title, Instant scheduledAt, Boolean dismissed) {}

  @Transactional
  public ReminderDto update(long userId, long id, UpdateRequest in) {
    Reminder r = reminders.findByIdAndUserId(id, userId).orElseThrow(() -> ApiException.notFound("We could not find that reminder."));
    if (Boolean.TRUE.equals(in.dismissed())) { r.status = "DISMISSED"; r.note = null; return toDto(reminders.save(r)); }
    if (!"CUSTOM".equals(r.type)) throw ApiException.badRequest("Only reminders you created can be edited. You can dismiss this one.", null);
    String title = in.title() == null ? r.title : com.docket.web.Strings.blank(in.title());
    if (title == null || title.length() > 200) throw ApiException.badRequest("Enter a title of up to 200 characters.", Map.of("title", "Enter a title of up to 200 characters"));
    r.title = title;
    if (in.scheduledAt() != null) {
      if (!in.scheduledAt().isAfter(Instant.now())) throw ApiException.badRequest("Choose a time in the future.", Map.of("scheduledAt", "Choose a time in the future"));
      r.eventAt = in.scheduledAt(); r.remindAt = in.scheduledAt(); r.status = "PENDING"; r.attempts = 0; r.note = null; r.sentAt = null;
    }
    return toDto(reminders.save(r));
  }

  private static ReminderDto toDto(Reminder r) {
    return new ReminderDto(r.id, r.applicationId, r.title, r.type, r.remindAt, r.eventAt, r.status, r.note, r.sentAt);
  }

  // ---------- generation and delivery ----------

  /**
   * Best-effort sync after application/interview writes. Runs after the caller's transaction commits
   * (so new rows are visible) in a separate transaction, and never throws into the caller.
   */
  public void syncQuietly(long userId) {
    Runnable run = () -> {
      try { self.syncInNewTx(userId, Instant.now()); }
      catch (RuntimeException ignored) { /* do not fail the application/interview write */ }
    };
    if (TransactionSynchronizationManager.isSynchronizationActive()) {
      TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
        @Override public void afterCommit() { run.run(); }
      });
    } else {
      run.run();
    }
  }

  @Transactional(propagation = Propagation.REQUIRES_NEW)
  public void syncInNewTx(long userId, Instant now) { sync(userId, now); }

  /** Makes the stored reminders match the user's current records. Idempotent: safe to call as often as needed. */
  @Transactional
  public void sync(long userId, Instant now) {
    ReminderPreference pref = prefsRepo.findById(userId).orElseGet(() -> new ReminderPreference(userId));
    List<PlanApp> planApps = apps.findByUserIdAndDeletedAtIsNullOrderByApplicationDateDescIdDesc(userId).stream()
        .map(a -> new PlanApp(a.getId(), a.getCompanyName(), a.getPosition(), !Vocab.isClosed(a.getCurrentStatus()), a.getDeadline(), a.getFollowUpDate())).toList();
    List<PlanInterview> planIvs = interviews.findWithApplication(userId, 0L).stream().map(r -> {
      Interview i = (Interview) r[0];
      return new PlanInterview(i.getId(), i.getApplicationId(), i.getInterviewDate(), i.getRound(), i.getFollowUpDate());
    }).toList();
    List<Planned> planned = ReminderPlanner.plan(planApps, planIvs, planPrefs(pref), now);

    Set<String> wanted = new HashSet<>();
    for (Planned p : planned) wanted.add(p.dedupKey());
    Map<String, Reminder> existing = new HashMap<>();
    if (!wanted.isEmpty()) for (Reminder r : reminders.findByUserIdAndDedupKeyIn(userId, wanted)) existing.put(r.dedupKey, r);
    for (Planned p : planned) {
      if (!existing.containsKey(p.dedupKey()))
        reminders.save(new Reminder(userId, p.appId(), p.type(), p.dedupKey(), p.title(), p.eventAt(), p.remindAt()));
    }

    // Pending generated reminders that no longer match any record (deadline moved, application closed or deleted, ...) are skipped, not sent.
    for (Reminder r : reminders.findByUserIdAndStatus(userId, "PENDING")) {
      if ("CUSTOM".equals(r.type)) continue;
      if (!wanted.contains(r.dedupKey)) { r.status = "SKIPPED"; r.note = "No longer applies."; }
      else if (!pref.emailEnabled && !r.remindAt.isAfter(now)) { r.status = "SKIPPED"; r.note = "Email reminders were off."; }
    }
    if (!pref.emailEnabled) {
      for (Reminder r : reminders.findByUserIdAndStatus(userId, "PENDING"))
        if ("CUSTOM".equals(r.type) && !r.remindAt.isAfter(now)) { r.status = "SKIPPED"; r.note = "Email reminders were off."; }
    }
  }

  /** Sends what is due for one user. Called by the background job for users who opted in. */
  @Transactional
  public int deliverDue(long userId, Instant now) {
    sync(userId, now);
    ReminderPreference pref = prefsRepo.findById(userId).orElse(null);
    if (pref == null || !pref.emailEnabled) return 0;
    User user = users.findById(userId).orElse(null);
    if (user == null) return 0;
    ZoneId zone = planPrefs(pref).zone();
    int sent = 0;
    for (Reminder r : reminders.findByUserIdAndStatus(userId, "PENDING")) {
      if (r.remindAt.isAfter(now)) continue;
      if (!r.eventAt.isAfter(now)) { r.status = "SKIPPED"; r.note = "The event had already passed."; continue; }
      String when = DateTimeFormatter.ofPattern("EEE d MMM yyyy, h:mm a z", Locale.ENGLISH).format(r.eventAt.atZone(zone));
      if ("FOLLOW_UP".equals(r.type)) when = DateTimeFormatter.ofPattern("EEE d MMM yyyy", Locale.ENGLISH).format(r.remindAt.atZone(zone));
      String path = r.applicationId == null ? "/app/calendar" : "/app/applications/" + r.applicationId;
      String error = mail.sendReminder(user.getEmail(), user.getName(), r.title, when, path);
      r.attempts++; r.lastAttemptAt = now;
      if (error == null) { r.status = "SENT"; r.sentAt = now; r.note = "Accepted by the mail server."; sent++; }
      else if (r.attempts >= MAX_ATTEMPTS) { r.status = "FAILED"; r.note = "Could not be sent after " + MAX_ATTEMPTS + " tries."; }
      else r.note = "Last attempt failed; will retry.";
    }
    return sent;
  }

  public List<Long> usersToProcess() { return prefsRepo.emailEnabledUserIds(); }

  @Transactional
  public int purgeOld() { return reminders.purgeEventsBefore(Instant.now().minus(Duration.ofDays(60))); }
}
