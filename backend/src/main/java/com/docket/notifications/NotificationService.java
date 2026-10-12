package com.docket.notifications;

import com.docket.applications.ApplicationRepository;
import com.docket.applications.Vocab;
import com.docket.interviews.Interview;
import com.docket.interviews.InterviewRepository;
import com.docket.notifications.NotificationPlanner.*;
import com.docket.reminders.ReminderPreference;
import com.docket.reminders.ReminderPreferenceRepository;
import com.docket.user.UserRepository;
import com.docket.web.ApiException;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.springframework.context.annotation.Lazy;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

@Service
public class NotificationService {
  private final NotificationRepository repo;
  private final ApplicationRepository apps;
  private final InterviewRepository interviews;
  private final ReminderPreferenceRepository prefsRepo;
  private final UserRepository users;
  private final NotificationService self;

  public NotificationService(NotificationRepository repo, ApplicationRepository apps, InterviewRepository interviews,
                             ReminderPreferenceRepository prefsRepo, UserRepository users, @Lazy NotificationService self) {
    this.repo = repo; this.apps = apps; this.interviews = interviews; this.prefsRepo = prefsRepo; this.users = users; this.self = self;
  }

  public record NotificationDto(long id, Long applicationId, String category, String title, String body, String href,
                                boolean read, Instant createdAt) {}

  public record Feed(List<NotificationDto> items, long unreadCount) {}

  @Transactional(readOnly = true)
  public Feed list(long userId) {
    List<NotificationDto> items = repo.recent(userId, PageRequest.of(0, 50)).stream().map(NotificationService::toDto).toList();
    return new Feed(items, repo.countByUserIdAndReadAtIsNull(userId));
  }

  @Transactional
  public NotificationDto markRead(long userId, long id) {
    Notification n = repo.findByIdAndUserId(id, userId).orElseThrow(() -> ApiException.notFound("We could not find that notification."));
    if (n.readAt == null) { n.readAt = Instant.now(); n = repo.save(n); }
    return toDto(n);
  }

  @Transactional
  public int markAllRead(long userId) { return repo.markAllRead(userId, Instant.now()); }

  /**
   * Runs after the caller's transaction commits so newly saved interviews/deadlines are visible,
   * in a separate transaction so failures never roll back the caller's write.
   */
  public void syncQuietly(long userId) {
    Runnable run = () -> {
      try { self.syncInNewTx(userId, Instant.now()); }
      catch (RuntimeException ignored) { /* never fail the caller's write */ }
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

  @Transactional
  public void sync(long userId, Instant now) {
    ReminderPreference pref = prefsRepo.findById(userId).orElseGet(() -> new ReminderPreference(userId));
    ZoneId zone;
    try { zone = ZoneId.of(pref.getTimeZone()); } catch (Exception e) { zone = ZoneId.of("UTC"); }
    Prefs prefs = new Prefs(pref.getDeadlineLeadHours(), pref.getInterviewLeadHours(), pref.isFollowUpEnabled(), zone);

    List<PlanApp> planApps = apps.findByUserIdAndDeletedAtIsNullOrderByApplicationDateDescIdDesc(userId).stream()
        .map(a -> new PlanApp(a.getId(), a.getCompanyName(), a.getPosition(), !Vocab.isClosed(a.getCurrentStatus()),
            a.getDeadline(), a.getFollowUpDate(), a.getCurrentStatus())).toList();
    List<PlanInterview> planIvs = interviews.findWithApplication(userId, 0L).stream().map(r -> {
      Interview i = (Interview) r[0];
      return new PlanInterview(i.getId(), i.getApplicationId(), i.getInterviewDate(), i.getRound(), i.getResult(), i.getFollowUpDate());
    }).toList();

    List<Planned> planned = NotificationPlanner.plan(planApps, planIvs, prefs, now);
    Set<String> wanted = new HashSet<>();
    for (Planned p : planned) wanted.add(p.dedupKey());
    Map<String, Notification> existing = new HashMap<>();
    if (!wanted.isEmpty()) for (Notification n : repo.findByUserIdAndDedupKeyIn(userId, wanted)) existing.put(n.dedupKey, n);
    for (Planned p : planned) {
      if (existing.containsKey(p.dedupKey())) continue;
      repo.save(new Notification(userId, p.appId(), p.category(), p.dedupKey(), p.title(), p.body(), p.href()));
    }
  }

  public List<Long> allUserIdsWithApps() {
    return users.findAll().stream().map(u -> u.getId()).toList();
  }

  @Transactional
  public int purgeOld() { return repo.purgeOlderThan(Instant.now().minus(Duration.ofDays(90))); }

  private static NotificationDto toDto(Notification n) {
    return new NotificationDto(n.id, n.applicationId, n.category, n.title, n.body, n.href, n.readAt != null, n.createdAt);
  }
}
