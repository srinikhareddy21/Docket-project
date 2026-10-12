package com.docket.applications;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

/**
 * Transparent, deterministic priority score (0-100). Same formula as frontend/src/utils/priority.js.
 *
 *   Deadline   passed 50 | within 48h 55 | within 7d 30 | within 14d 15
 *   Interview  next one within 48h 50 | within 7d 30 | later 10
 *   Prep gap   interview within 7d and preparation incomplete  +15
 *   Follow-up  overdue 35 | due today 25
 *   Staleness  Applied/Shortlisted with no status change in 14+ days  +5  (small on purpose)
 *
 * Levels: High >= 50, Medium 25-49, Low < 25. Closed applications always score 0.
 * A single interview or deadline within ~24–48 hours is High on its own.
 * Follow-up dates are whole days compared with "today" in UTC.
 */
public final class Priority {
  private Priority() {}

  public record Result(int score, String level, List<String> reasons, String nextAction) {}

  private static final Duration DAY = Duration.ofDays(1);

  public static Result compute(String status, Instant deadline, LocalDate followUp, Instant statusChangedAt,
                               Instant nextInterviewAt, boolean prepIncomplete, Instant now, LocalDate today) {
    if (Vocab.isClosed(status)) return new Result(0, "Low", List.of("Application is closed."), null);

    int score = 0;
    List<String> reasons = new ArrayList<>();
    int bestPoints = 0;
    String action = null;

    if (deadline != null) {
      Duration diff = Duration.between(now, deadline);
      int pts = 0; String why = null; String act = null;
      if (diff.isNegative()) { pts = 50; why = "Deadline has passed."; act = "Deadline passed"; }
      else if (diff.compareTo(DAY.multipliedBy(2)) <= 0) { pts = 55; why = "Deadline within 48 hours."; act = "Due within 48 hours"; }
      else if (diff.compareTo(DAY.multipliedBy(7)) <= 0) { pts = 30; why = "Deadline within 7 days."; act = "Due in " + diff.toDays() + " days"; }
      else if (diff.compareTo(DAY.multipliedBy(14)) <= 0) { pts = 15; why = "Deadline within 14 days."; act = "Due in " + diff.toDays() + " days"; }
      if (pts > 0) { score += pts; reasons.add(why); if (pts > bestPoints) { bestPoints = pts; action = act; } }
    }

    if (nextInterviewAt != null) {
      Duration diff = Duration.between(now, nextInterviewAt);
      int pts; String why;
      if (diff.compareTo(DAY.multipliedBy(2)) <= 0) { pts = 50; why = "Interview within 48 hours."; }
      else if (diff.compareTo(DAY.multipliedBy(7)) <= 0) { pts = 30; why = "Interview within 7 days."; }
      else { pts = 10; why = "Interview scheduled."; }
      score += pts; reasons.add(why);
      if (pts > bestPoints) { bestPoints = pts; action = "Prepare for interview"; }
      if (diff.compareTo(DAY.multipliedBy(7)) <= 0 && prepIncomplete) {
        score += 15; reasons.add("Interview preparation is incomplete.");
        if (15 > bestPoints) { bestPoints = 15; action = "Finish interview preparation"; }
      }
    }

    if (followUp != null) {
      if (followUp.isBefore(today)) { score += 35; reasons.add("Follow-up is overdue."); if (35 > bestPoints) { bestPoints = 35; action = "Send follow-up"; } }
      else if (followUp.isEqual(today)) { score += 25; reasons.add("Follow-up due today."); if (25 > bestPoints) { bestPoints = 25; action = "Follow up today"; } }
    }

    if (statusChangedAt != null && Vocab.WAITING.contains(status) && Duration.between(statusChangedAt, now).compareTo(DAY.multipliedBy(14)) > 0) {
      score += 5; reasons.add("No update in over 14 days.");
      if (5 > bestPoints) { action = "Check for an update"; }
    }

    score = Math.min(100, score);
    String level = score >= 50 ? "High" : score >= 25 ? "Medium" : "Low";
    if (reasons.isEmpty()) reasons.add("Nothing time-sensitive right now.");
    return new Result(score, level, List.copyOf(reasons), action);
  }
}
