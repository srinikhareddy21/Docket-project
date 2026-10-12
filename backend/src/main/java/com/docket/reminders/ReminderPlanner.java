package com.docket.reminders;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;

/**
 * Decides which reminders should exist, from plain rows. No database or Spring, so it can be tested alone.
 *
 *  - Deadline:  open application, deadline in the future (within 30 days). Remind deadline_lead_hours before.
 *  - Interview: open application, interview in the future (within 30 days). Remind interview_lead_hours before.
 *  - Follow-up: open application's follow-up date, and each interview's follow-up date. Remind at 09:00 on that
 *               day in the user's time zone, as long as that day has not finished yet. Needs follow_up_enabled.
 * A reminder whose remind time has already passed but whose event has not is still created (it is sent at once).
 * Closed applications (Selected, Rejected, Withdrawn) never get reminders.
 */
public final class ReminderPlanner {
  private ReminderPlanner() {}

  public static final int HORIZON_DAYS = 30;

  public record PlanApp(long id, String company, String position, boolean open, Instant deadline, LocalDate followUp) {}
  public record PlanInterview(long id, long appId, Instant at, String round, LocalDate followUp) {}
  public record Prefs(boolean emailEnabled, int deadlineLeadHours, int interviewLeadHours, boolean followUpEnabled, ZoneId zone) {}
  public record Planned(String type, Long appId, String dedupKey, String title, Instant eventAt, Instant remindAt) {}

  public static List<Planned> plan(List<PlanApp> apps, List<PlanInterview> interviews, Prefs prefs, Instant now) {
    Instant horizon = now.plus(Duration.ofDays(HORIZON_DAYS));
    List<Planned> out = new ArrayList<>();
    java.util.Map<Long, PlanApp> byId = new java.util.HashMap<>();
    for (PlanApp a : apps) byId.put(a.id(), a);

    for (PlanApp a : apps) {
      if (!a.open()) continue;
      String who = a.company() + " — " + a.position();
      if (a.deadline() != null && a.deadline().isAfter(now) && !a.deadline().isAfter(horizon))
        out.add(new Planned("DEADLINE", a.id(), key("DEADLINE", a.id(), 0, a.deadline()), "Deadline: " + who, a.deadline(),
            a.deadline().minus(Duration.ofHours(prefs.deadlineLeadHours()))));
      if (prefs.followUpEnabled() && a.followUp() != null) followUp(out, prefs, now, horizon, a.id(), 0, a.followUp(), "Follow up: " + who);
    }
    for (PlanInterview i : interviews) {
      PlanApp a = byId.get(i.appId());
      if (a == null || !a.open()) continue;
      String who = a.company() + " — " + a.position();
      if (i.at().isAfter(now) && !i.at().isAfter(horizon))
        out.add(new Planned("INTERVIEW", a.id(), key("INTERVIEW", a.id(), i.id(), i.at()), i.round() + " interview: " + who, i.at(),
            i.at().minus(Duration.ofHours(prefs.interviewLeadHours()))));
      if (prefs.followUpEnabled() && i.followUp() != null)
        followUp(out, prefs, now, horizon, a.id(), i.id(), i.followUp(), "Follow up after " + i.round() + " interview: " + who);
    }
    return out;
  }

  private static void followUp(List<Planned> out, Prefs prefs, Instant now, Instant horizon, long appId, long sourceId, LocalDate day, String title) {
    Instant remindAt = day.atTime(9, 0).atZone(prefs.zone()).toInstant();
    Instant eventAt = day.plusDays(1).atStartOfDay(prefs.zone()).toInstant();   // the follow-up day ends here
    if (eventAt.isAfter(now) && !remindAt.isAfter(horizon))
      out.add(new Planned("FOLLOW_UP", appId, key("FOLLOW_UP", appId, sourceId, remindAt), title, eventAt, remindAt));
  }

  static String key(String type, long appId, long sourceId, Instant at) {
    return type + ":" + appId + ":" + sourceId + ":" + at.getEpochSecond();
  }
}
