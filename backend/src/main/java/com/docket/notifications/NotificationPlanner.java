package com.docket.notifications;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Decides which in-app notifications should exist from plain rows. No Spring/DB.
 * Lead time defaults match reminder defaults (deadline 48h, interview/assessment 24h).
 * Dedup keys make generation idempotent across refreshes and scheduler runs.
 */
public final class NotificationPlanner {
  private NotificationPlanner() {}

  public static final int HORIZON_DAYS = 14;

  public record PlanApp(long id, String company, String position, boolean open, Instant deadline, LocalDate followUp, String status) {}
  public record PlanInterview(long id, long appId, Instant at, String round, String result, LocalDate followUp) {}
  public record Prefs(int deadlineLeadHours, int interviewLeadHours, boolean followUpEnabled, ZoneId zone) {}
  public record Planned(String category, Long appId, String dedupKey, String title, String body, String href) {}

  public static List<Planned> plan(List<PlanApp> apps, List<PlanInterview> interviews, Prefs prefs, Instant now) {
    Instant horizon = now.plus(Duration.ofDays(HORIZON_DAYS));
    List<Planned> out = new ArrayList<>();
    java.util.Map<Long, PlanApp> byId = new java.util.HashMap<>();
    for (PlanApp a : apps) byId.put(a.id(), a);
    DateTimeFormatter fmt = DateTimeFormatter.ofPattern("EEE d MMM yyyy, h:mm a", Locale.ENGLISH).withZone(prefs.zone());

    for (PlanApp a : apps) {
      if (!a.open()) continue;
      String who = a.company() + " — " + a.position();
      String href = "/app/applications/" + a.id();
      if (a.deadline() != null) {
        if (a.deadline().isBefore(now)) {
          out.add(new Planned("DEADLINE", a.id(), key("DEADLINE", a.id(), 0, a.deadline()),
              "Deadline passed: " + who, "The application deadline has passed.", href));
        } else if (!a.deadline().isAfter(horizon)) {
          Instant lead = a.deadline().minus(Duration.ofHours(prefs.deadlineLeadHours()));
          if (!lead.isAfter(now) || Duration.between(now, a.deadline()).compareTo(Duration.ofHours(prefs.deadlineLeadHours())) <= 0) {
            out.add(new Planned("DEADLINE", a.id(), key("DEADLINE", a.id(), 0, a.deadline()),
                "Deadline soon: " + who, "Due " + fmt.format(a.deadline()) + ".", href));
          }
        }
      }
      if (prefs.followUpEnabled() && a.followUp() != null && !a.followUp().isAfter(LocalDate.now(prefs.zone()))) {
        Instant day = a.followUp().atStartOfDay(prefs.zone()).toInstant();
        out.add(new Planned("FOLLOW_UP", a.id(), key("FOLLOW_UP", a.id(), 0, day),
            "Follow up: " + who, a.followUp().isBefore(LocalDate.now(prefs.zone())) ? "Follow-up is overdue." : "Follow-up is due today.", href));
      }
      if ("Interview".equals(a.status()) || "Assessment".equals(a.status())) {
        boolean hasUpcoming = interviews.stream().anyMatch(i -> i.appId() == a.id() && !i.at().isBefore(now));
        if (!hasUpcoming) {
          out.add(new Planned("SYSTEM", a.id(), "STATUS_NO_DATE:" + a.id() + ":" + a.status(),
              a.status() + " without a date: " + who,
              "Status is " + a.status() + " but no date/time is recorded yet.", href));
        }
      }
    }

    for (PlanInterview i : interviews) {
      PlanApp a = byId.get(i.appId());
      if (a == null || !a.open()) continue;
      String who = a.company() + " — " + a.position();
      String href = "/app/applications/" + a.id();
      boolean assessment = "Assessment".equalsIgnoreCase(i.round());
      String cat = assessment ? "ASSESSMENT" : "INTERVIEW";
      String label = assessment ? "Assessment" : (i.round() + " interview");
      if (!i.at().isBefore(now) && !i.at().isAfter(horizon)) {
        Instant lead = i.at().minus(Duration.ofHours(prefs.interviewLeadHours()));
        if (!lead.isAfter(now) || Duration.between(now, i.at()).compareTo(Duration.ofHours(prefs.interviewLeadHours())) <= 0) {
          out.add(new Planned(cat, a.id(), key(cat, a.id(), i.id(), i.at()),
              label + " soon: " + who, "Scheduled for " + fmt.format(i.at()) + ".", href));
        }
      } else if (i.at().isBefore(now) && ("Pending".equals(i.result()) || "Awaiting outcome".equals(i.result()))) {
        out.add(new Planned(cat, a.id(), key(cat + "_OUTCOME", a.id(), i.id(), i.at()),
            label + " needs outcome: " + who, "The event has passed with no recorded result.", href));
      }
      if (prefs.followUpEnabled() && i.followUp() != null && !i.followUp().isAfter(LocalDate.now(prefs.zone()))) {
        Instant day = i.followUp().atStartOfDay(prefs.zone()).toInstant();
        out.add(new Planned("FOLLOW_UP", a.id(), key("FOLLOW_UP", a.id(), i.id(), day),
            "Follow up after " + label + ": " + who,
            i.followUp().isBefore(LocalDate.now(prefs.zone())) ? "Follow-up is overdue." : "Follow-up is due today.", href));
      }
    }
    return out;
  }

  static String key(String type, long appId, long sourceId, Instant at) {
    return type + ":" + appId + ":" + sourceId + ":" + at.getEpochSecond();
  }
}
