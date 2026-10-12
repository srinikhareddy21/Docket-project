package com.docket.dashboard;

import com.docket.applications.Priority;
import com.docket.applications.Vocab;
import java.time.DayOfWeek;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.time.temporal.TemporalAdjusters;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * All dashboard and analytics numbers, calculated from plain rows with no database or Spring involved,
 * so the formulas can be tested on their own. Every figure comes from the caller's real records.
 *
 * Definitions (also in README):
 *  - Funnel stage reached: the furthest of Applied, Shortlisted, Assessment, Interview, Selected that an application
 *    has ever had (from its status history), so an application rejected after an interview still counts as having
 *    reached Interview. An interview record also counts as reaching Interview. Rejected/Withdrawn are not stages.
 *  - Interview rate = reached Interview or beyond / all applications.
 *  - Offer rate     = currently Selected / reached Interview or beyond. (Selected always counts as reached, so it cannot exceed 100%.)
 *  - Submitted this week = applications whose application date falls in the current Monday-Sunday week (UTC).
 *  - Upcoming deadlines = open applications (not Selected/Rejected/Withdrawn) with a deadline in the next 14 days.
 *  - Interviews scheduled = interviews dated from now onward.
 */
public final class Metrics {
  private Metrics() {}

  public static final List<String> STAGES = List.of("Applied", "Shortlisted", "Assessment", "Interview", "Selected");
  private static final int INTERVIEW_RANK = STAGES.indexOf("Interview");
  public static final int UPCOMING_DEADLINE_DAYS = 14;
  public static final int INTERVIEW_HORIZON_DAYS = 7;
  public static final int TREND_MAX_WEEKS = 26;
  public static final String UNSPECIFIED_SOURCE = "Unspecified";
  public static final List<String> MATCH_BUCKETS = List.of("0–25%", "26–50%", "51–75%", "76–100%");

  // ---- inputs ----
  public record AppRow(long id, String company, String position, String status, String source, LocalDate appliedOn,
                       Instant deadline, LocalDate followUp, Instant statusChangedAt, Double matchScore) {}
  public record HistRow(long appId, String previousStatus, String newStatus, Instant at) {}
  public record IvRow(long id, long appId, Instant at, String round, String result, LocalDate followUp, Instant createdAt) {}
  public record TaskRow(long appId, String text, Instant at) {}

  // ---- outputs ----
  public record Stage(String stage, int count) {}
  public record SourceRow(String source, int applications, int interviews, int offers) {}
  public record Period(String period, int count) {}
  public record Count(String label, int count) {}
  public record Recent(String message, Instant at, Long applicationId) {}
  public record Action(long applicationId, String company, String position, String message, Instant date,
                       String urgency, String actionLabel, int priorityScore) {}
  public record Insights(int deadlinesOverdue, int deadlinesNext7Days, int followUpsDue, int interviewsAwaitingOutcome) {}
  public record Summary(int total, int thisWeek, int interviewsScheduled, int offers, int upcomingDeadlines, int reachedInterview,
                        List<Stage> funnel, List<SourceRow> sources, List<Recent> recent, List<Action> actions,
                        List<Count> statuses, List<Count> skillMatch, Insights insights) {}

  public record Input(List<AppRow> apps, List<HistRow> history, List<IvRow> interviews, Map<Long, Integer> prepIncomplete,
                      List<TaskRow> completedTasks, Instant now, int actionLimit) {}

  public static LocalDate weekStart(LocalDate day) { return day.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY)); }

  private static boolean open(AppRow a) { return !Vocab.isClosed(a.status()); }

  // ---------- main entry ----------

  public static Summary summary(Input in) {
    LocalDate today = in.now().atZone(ZoneOffset.UTC).toLocalDate();
    LocalDate wk = weekStart(today);
    Map<Long, Integer> rank = reachedRank(in.apps(), in.history(), in.interviews());

    int thisWeek = 0, offers = 0, reached = 0, upcoming = 0;
    for (AppRow a : in.apps()) {
      if (a.appliedOn() != null && !a.appliedOn().isBefore(wk) && !a.appliedOn().isAfter(wk.plusDays(6))) thisWeek++;
      if ("Selected".equals(a.status())) offers++;
      if (rank.get(a.id()) >= INTERVIEW_RANK) reached++;
      if (open(a) && a.deadline() != null && !a.deadline().isBefore(in.now())
          && !a.deadline().isAfter(in.now().plus(Duration.ofDays(UPCOMING_DEADLINE_DAYS)))) upcoming++;
    }
    int scheduled = (int) in.interviews().stream().filter(i -> !i.at().isBefore(in.now())).count();

    List<Action> all = actions(in);
    return new Summary(in.apps().size(), thisWeek, scheduled, offers, upcoming, reached,
        funnel(in.apps(), rank), sources(in.apps(), rank), recent(in), all.subList(0, Math.min(all.size(), in.actionLimit())),
        statuses(in.apps()), skillMatch(in.apps()), insights(in, all));
  }

  // ---------- funnel ----------

  /** Furthest stage index each application has reached. */
  public static Map<Long, Integer> reachedRank(List<AppRow> apps, List<HistRow> history, List<IvRow> interviews) {
    Map<Long, Integer> rank = new HashMap<>();
    for (AppRow a : apps) rank.put(a.id(), Math.max(0, STAGES.indexOf(a.status())));
    for (HistRow h : history) {
      int r = STAGES.indexOf(h.newStatus());
      rank.computeIfPresent(h.appId(), (k, v) -> Math.max(v, r));
    }
    for (IvRow i : interviews) rank.computeIfPresent(i.appId(), (k, v) -> Math.max(v, INTERVIEW_RANK));
    return rank;
  }

  public static List<Stage> funnel(List<AppRow> apps, Map<Long, Integer> rank) {
    List<Stage> out = new ArrayList<>();
    for (int s = 0; s < STAGES.size(); s++) {
      final int stage = s;
      out.add(new Stage(STAGES.get(s), (int) apps.stream().filter(a -> rank.get(a.id()) >= stage).count()));
    }
    return out;
  }

  // ---------- sources, statuses, skill match ----------

  public static List<SourceRow> sources(List<AppRow> apps, Map<Long, Integer> rank) {
    Map<String, int[]> by = new LinkedHashMap<>();
    for (AppRow a : apps) {
      String key = a.source() == null || a.source().isBlank() ? UNSPECIFIED_SOURCE : a.source();
      int[] c = by.computeIfAbsent(key, k -> new int[3]);
      c[0]++;
      if (rank.get(a.id()) >= INTERVIEW_RANK) c[1]++;
      if ("Selected".equals(a.status())) c[2]++;
    }
    return by.entrySet().stream().map(e -> new SourceRow(e.getKey(), e.getValue()[0], e.getValue()[1], e.getValue()[2]))
        .sorted(Comparator.comparingInt(SourceRow::applications).reversed().thenComparing(SourceRow::source)).toList();
  }

  /** Current status counts, in workflow order, zero-count statuses omitted. */
  public static List<Count> statuses(List<AppRow> apps) {
    List<String> order = List.of("Applied", "Shortlisted", "Assessment", "Interview", "Selected", "Rejected", "Withdrawn");
    Map<String, Long> n = apps.stream().collect(Collectors.groupingBy(AppRow::status, Collectors.counting()));
    return order.stream().filter(n::containsKey).map(s -> new Count(s, n.get(s).intValue())).toList();
  }

  /** Match-score buckets for applications that have a score, plus how many had no skill data. */
  public static List<Count> skillMatch(List<AppRow> apps) {
    int[] b = new int[4]; int none = 0;
    for (AppRow a : apps) {
      Double s = a.matchScore();
      if (s == null) { none++; continue; }
      b[s <= 25 ? 0 : s <= 50 ? 1 : s <= 75 ? 2 : 3]++;
    }
    List<Count> out = new ArrayList<>();
    for (int i = 0; i < 4; i++) out.add(new Count(MATCH_BUCKETS.get(i), b[i]));
    out.add(new Count("Not enough skill data", none));
    return out;
  }

  // ---------- trends ----------

  /** Applications per week (Monday date), zero-filled between the first and last week, at most the latest 26 weeks. */
  public static List<Period> trends(List<AppRow> apps) {
    Map<LocalDate, Integer> weeks = new HashMap<>();
    for (AppRow a : apps) if (a.appliedOn() != null) weeks.merge(weekStart(a.appliedOn()), 1, Integer::sum);
    if (weeks.isEmpty()) return List.of();
    LocalDate first = weeks.keySet().stream().min(LocalDate::compareTo).get();
    LocalDate last = weeks.keySet().stream().max(LocalDate::compareTo).get();
    LocalDate from = last.minusWeeks(TREND_MAX_WEEKS - 1L);
    if (first.isAfter(from)) from = first;
    List<Period> out = new ArrayList<>();
    for (LocalDate w = from; !w.isAfter(last); w = w.plusWeeks(1)) out.add(new Period(w.toString(), weeks.getOrDefault(w, 0)));
    return out;
  }

  // ---------- needs-action list ----------

  public static List<Action> actions(Input in) {
    Instant now = in.now();
    LocalDate today = now.atZone(ZoneOffset.UTC).toLocalDate();
    Map<Long, AppRow> byId = new HashMap<>();
    for (AppRow a : in.apps()) byId.put(a.id(), a);

    Map<Long, Instant> nextIv = new HashMap<>();
    for (IvRow i : in.interviews()) if (!i.at().isBefore(now)) nextIv.merge(i.appId(), i.at(), (x, y) -> x.isBefore(y) ? x : y);

    Map<Long, Integer> score = new HashMap<>();
    for (AppRow a : in.apps()) {
      Priority.Result p = Priority.compute(a.status(), a.deadline(), a.followUp(), a.statusChangedAt(),
          nextIv.get(a.id()), in.prepIncomplete().containsKey(a.id()), now, today);
      score.put(a.id(), p.score());
    }

    List<Action> out = new ArrayList<>();
    for (AppRow a : in.apps()) {
      if (!open(a)) continue;
      int ps = score.get(a.id());
      if (a.deadline() != null) {
        Duration d = Duration.between(now, a.deadline());
        if (d.isNegative()) out.add(act(a, "Deadline has passed.", a.deadline(), "High", "Review", ps));
        else if (d.compareTo(Duration.ofHours(48)) <= 0) out.add(act(a, "Deadline within 48 hours.", a.deadline(), "High", "Review", ps));
      }
      if (a.followUp() != null && !a.followUp().isAfter(today)) {
        boolean overdue = a.followUp().isBefore(today);
        out.add(act(a, overdue ? "Follow-up is overdue." : "Follow-up is due today.", a.followUp().atStartOfDay().toInstant(ZoneOffset.UTC),
            overdue ? "High" : "Medium", "Follow up", ps));
      }
      // Status "Interview" without an interview record means the date/time was never entered.
      if ("Interview".equals(a.status()) && !nextIv.containsKey(a.id())) {
        out.add(act(a, "Interview status is set, but no interview date is recorded.", now, "High", "Add interview", ps));
      }
    }

    for (IvRow i : in.interviews()) {
      AppRow a = byId.get(i.appId());
      if (a == null || !open(a)) continue;
      int ps = score.get(a.id());
      boolean undecided = "Pending".equals(i.result()) || "Awaiting outcome".equals(i.result());
      if (!i.at().isBefore(now)) {
        Duration d = Duration.between(now, i.at());
        if (d.compareTo(Duration.ofDays(INTERVIEW_HORIZON_DAYS)) <= 0) {
          boolean soon = d.compareTo(Duration.ofHours(48)) <= 0;
          boolean assessment = "Assessment".equalsIgnoreCase(i.round());
          String label = assessment ? "Assessment coming up." : (i.round() + " interview coming up.");
          out.add(act(a, label, i.at(), soon ? "High" : "Medium", assessment ? "Prepare assessment" : "Prepare", ps));
          Integer left = in.prepIncomplete().get(a.id());
          if (left != null && left > 0)
            out.add(act(a, "Preparation incomplete (" + left + " item" + (left == 1 ? "" : "s") + " left).", i.at(), soon ? "High" : "Medium", "Finish prep", ps));
        }
      } else if (undecided) {
        boolean assessment = "Assessment".equalsIgnoreCase(i.round());
        out.add(act(a, (assessment ? "Assessment" : i.round() + " interview") + " has no recorded outcome.", i.at(), "Medium", "Record outcome", ps));
      }
      if (i.followUp() != null && !i.followUp().isAfter(today))
        out.add(act(a, i.round() + " interview follow-up " + (i.followUp().isBefore(today) ? "is overdue." : "is due today."),
            i.followUp().atStartOfDay().toInstant(ZoneOffset.UTC), i.followUp().isBefore(today) ? "High" : "Medium", "Follow up", ps));
    }

    Map<String, Integer> urg = Map.of("High", 0, "Medium", 1, "Low", 2);
    out.sort(Comparator.comparingInt(Action::priorityScore).reversed()
        .thenComparing(x -> urg.get(x.urgency())).thenComparing(Action::date).thenComparing(Action::applicationId));
    return out;
  }

  private static Action act(AppRow a, String msg, Instant date, String urgency, String label, int score) {
    return new Action(a.id(), a.company(), a.position(), msg, date, urgency, label, score);
  }

  private static Insights insights(Input in, List<Action> all) {
    Instant now = in.now();
    LocalDate today = now.atZone(ZoneOffset.UTC).toLocalDate();
    int overdue = 0, next7 = 0, fups = 0;
    for (AppRow a : in.apps()) {
      if (!open(a)) continue;
      if (a.deadline() != null) {
        if (a.deadline().isBefore(now)) overdue++;
        else if (!a.deadline().isAfter(now.plus(Duration.ofDays(7)))) next7++;
      }
      if (a.followUp() != null && !a.followUp().isAfter(today)) fups++;
    }
    Set<Long> openIds = in.apps().stream().filter(Metrics::open).map(AppRow::id).collect(Collectors.toSet());
    int awaiting = (int) in.interviews().stream().filter(i -> openIds.contains(i.appId()) && i.at().isBefore(now)
        && ("Pending".equals(i.result()) || "Awaiting outcome".equals(i.result()))).count();
    return new Insights(overdue, next7, fups, awaiting);
  }

  // ---------- recent activity ----------

  public static List<Recent> recent(Input in) {
    Map<Long, AppRow> byId = new HashMap<>();
    for (AppRow a : in.apps()) byId.put(a.id(), a);
    List<Recent> out = new ArrayList<>();
    for (HistRow h : in.history()) {
      AppRow a = byId.get(h.appId());
      if (a == null) continue;
      String who = a.company() + " — " + a.position();
      out.add(new Recent(h.previousStatus() == null ? "Added " + who + " (" + h.newStatus() + ")"
          : who + ": " + h.previousStatus() + " → " + h.newStatus(), h.at(), a.id()));
    }
    for (IvRow i : in.interviews()) {
      AppRow a = byId.get(i.appId());
      if (a != null) out.add(new Recent(i.round() + " interview added for " + a.company() + " — " + a.position(), i.createdAt(), a.id()));
    }
    for (TaskRow t : in.completedTasks()) {
      AppRow a = byId.get(t.appId());
      if (a != null) out.add(new Recent("Completed: " + t.text() + " (" + a.company() + ")", t.at(), a.id()));
    }
    out.sort(Comparator.comparing(Recent::at).reversed());
    return out.subList(0, Math.min(out.size(), 10));
  }
}
