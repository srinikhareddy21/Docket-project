package com.docket.dashboard;

import com.docket.applications.Application;
import com.docket.applications.ApplicationRepository;
import com.docket.applications.StatusHistoryRepository;
import com.docket.checklist.ChecklistRepository;
import com.docket.dashboard.Metrics.*;
import com.docket.interviews.Interview;
import com.docket.interviews.InterviewRepository;
import com.docket.applications.Csv;
import com.docket.skills.SkillMatcher;
import com.docket.skills.SkillRepository;
import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Loads one user's live records, then lets Metrics do the arithmetic. Every query is filtered by the owner's id. */
@Service
public class DashboardService {
  private static final int DASHBOARD_ACTIONS = 8;
  private static final int ALL_ACTIONS = 50;

  private final ApplicationRepository apps;
  private final StatusHistoryRepository history;
  private final InterviewRepository interviews;
  private final ChecklistRepository checklist;
  private final SkillRepository skills;

  public DashboardService(ApplicationRepository apps, StatusHistoryRepository history, InterviewRepository interviews,
                          ChecklistRepository checklist, SkillRepository skills) {
    this.apps = apps; this.history = history; this.interviews = interviews; this.checklist = checklist; this.skills = skills;
  }

  @Transactional(readOnly = true)
  public Summary summary(long userId) { return Metrics.summary(load(userId, DASHBOARD_ACTIONS)); }

  @Transactional(readOnly = true)
  public List<Action> actions(long userId) { return Metrics.summary(load(userId, ALL_ACTIONS)).actions(); }

  @Transactional(readOnly = true)
  public List<Period> trends(long userId) { return Metrics.trends(load(userId, 0).apps()); }

  /** Aggregate analytics CSV — distinct from the applications export. */
  @Transactional(readOnly = true)
  public byte[] exportAnalysisCsv(long userId) {
    Summary s = summary(userId);
    StringBuilder out = new StringBuilder();
    out.append(Csv.row(List.of("Metric", "Value", "Notes")));
    out.append(Csv.row(List.of("Total applications", String.valueOf(s.total()), "Live applications")));
    out.append(Csv.row(List.of("Submitted this week", String.valueOf(s.thisWeek()), "Application date in current Mon–Sun week (UTC)")));
    out.append(Csv.row(List.of("Interviews scheduled", String.valueOf(s.interviewsScheduled()), "Interview records dated from now on")));
    out.append(Csv.row(List.of("Offers", String.valueOf(s.offers()), "Status Selected")));
    out.append(Csv.row(List.of("Deadlines in 14 days", String.valueOf(s.upcomingDeadlines()), "Open applications")));
    out.append(Csv.row(List.of("Reached interview stage", String.valueOf(s.reachedInterview()), "From status history or interview records")));
    out.append(Csv.row(List.of("Interview rate %", s.total() == 0 ? "" : String.format(java.util.Locale.ROOT, "%.1f", 100.0 * s.reachedInterview() / s.total()), "reached interview ÷ total")));
    out.append(Csv.row(List.of("Offer rate %", s.reachedInterview() == 0 ? "" : String.format(java.util.Locale.ROOT, "%.1f", 100.0 * s.offers() / s.reachedInterview()), "offers ÷ reached interview")));
    if (s.insights() != null) {
      out.append(Csv.row(List.of("Deadlines overdue", String.valueOf(s.insights().deadlinesOverdue()), "Open applications")));
      out.append(Csv.row(List.of("Deadlines in 7 days", String.valueOf(s.insights().deadlinesNext7Days()), "Open applications")));
      out.append(Csv.row(List.of("Follow-ups due", String.valueOf(s.insights().followUpsDue()), "Today or overdue")));
      out.append(Csv.row(List.of("Interviews awaiting outcome", String.valueOf(s.insights().interviewsAwaitingOutcome()), "Past, no result")));
    }
    out.append("\n");
    out.append(Csv.row(List.of("Funnel stage", "Count", "")));
    for (Stage st : s.funnel()) out.append(Csv.row(List.of(st.stage(), String.valueOf(st.count()), "Reached this stage or later")));
    out.append("\n");
    out.append(Csv.row(List.of("Status", "Count", "")));
    for (Count c : s.statuses()) out.append(Csv.row(List.of(c.label(), String.valueOf(c.count()), "Current status")));
    out.append("\n");
    out.append(Csv.row(List.of("Source", "Applications", "Interviews", "Offers")));
    for (SourceRow r : s.sources()) out.append(Csv.row(List.of(r.source(), String.valueOf(r.applications()), String.valueOf(r.interviews()), String.valueOf(r.offers()))));
    out.append("\n");
    out.append(Csv.row(List.of("Skill match bucket", "Applications", "")));
    for (Count c : s.skillMatch()) out.append(Csv.row(List.of(c.label(), String.valueOf(c.count()), "Required skills matched ÷ listed")));
    out.append("\n");
    out.append(Csv.row(List.of("Week starting", "Applications", "")));
    for (Period p : Metrics.trends(load(userId, 0).apps())) out.append(Csv.row(List.of(p.period(), String.valueOf(p.count()), "")));
    return Csv.bytes(out.toString());
  }

  private Input load(long userId, int actionLimit) {
    List<String> mine = skills.names(userId);
    List<AppRow> rows = apps.findByUserIdAndDeletedAtIsNullOrderByApplicationDateDescIdDesc(userId).stream()
        .map(a -> new AppRow(a.getId(), a.getCompanyName(), a.getPosition(), a.getCurrentStatus(), a.getApplicationSource(),
            a.getApplicationDate(), a.getDeadline(), a.getFollowUpDate(), a.getStatusChangedAt(),
            SkillMatcher.score(mine, a.getRequiredSkills())))
        .toList();
    List<HistRow> hist = history.forLiveApplications(userId).stream()
        .map(h -> new HistRow(h.getApplicationId(), h.getPreviousStatus(), h.getNewStatus(), h.getChangedAt())).toList();
    List<IvRow> ivs = interviews.findWithApplication(userId, 0L).stream().map(r -> {
      Interview i = (Interview) r[0];
      return new IvRow(i.getId(), i.getApplicationId(), i.getInterviewDate(), i.getRound(), i.getResult(), i.getFollowUpDate(), i.getCreatedAt());
    }).toList();
    Map<Long, Integer> prep = new HashMap<>();
    for (Object[] r : checklist.incompletePrepCounts(userId)) prep.put((Long) r[0], ((Number) r[1]).intValue());
    List<TaskRow> tasks = checklist.recentlyCompleted(userId, PageRequest.of(0, 10)).stream()
        .map(c -> new TaskRow(c.getApplicationId(), c.getText(), c.getUpdatedAt())).toList();
    return new Input(rows, hist, ivs, prep, tasks, Instant.now(), actionLimit);
  }
}
