package com.docket.calendar;

import com.docket.util.MiniJson;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Decides which Google Calendar events should exist for a user and builds their JSON bodies. Pure logic (no Spring).
 * Privacy: events carry only company, role, round, mode, link and a DOCKET link. Notes, feedback and preparation
 * text never leave DOCKET, because calendars are often shared with other people.
 */
public final class CalendarPlanner {
  private CalendarPlanner() {}

  public record Prefs(boolean interviews, boolean followUps, boolean deadlines) {}
  public record AppRow(long id, String company, String position, String status, Instant deadline, LocalDate followUpDate) {}
  public record InterviewRow(long id, long applicationId, Instant at, String round, String mode, String meetingLink, LocalDate followUpDate) {}

  /** body is the JSON sent to Google (without "id"); hash identifies its content. */
  public record Desired(String key, Map<String, Object> body, String hash) {}
  public record Plan(Map<String, Desired> desired, Set<String> keep) {}

  static final Duration INTERVIEW_LENGTH = Duration.ofHours(1);
  static final Duration DEADLINE_LENGTH = Duration.ofMinutes(30);
  static final int MAX_EVENTS = 300;
  private static final Set<String> CLOSED = Set.of("Selected", "Rejected", "Withdrawn");

  /**
   * desired: events to create or update. keep: sources that still exist but lie in the past; their events are left
   * alone (history stays in the calendar). Anything mapped that is in neither set is stale and gets deleted.
   */
  public static Plan plan(List<AppRow> apps, List<InterviewRow> interviews, Prefs prefs, Instant now, String appUrlBase) {
    Map<String, Desired> desired = new LinkedHashMap<>();
    Set<String> keep = new HashSet<>();
    LocalDate today = now.atZone(ZoneOffset.UTC).toLocalDate();
    Map<Long, AppRow> byId = new LinkedHashMap<>();
    for (AppRow a : apps) byId.put(a.id(), a);

    for (AppRow a : apps) {
      boolean open = !CLOSED.contains(a.status());
      String link = appUrlBase + "/app/applications/" + a.id();
      if (prefs.deadlines() && a.deadline() != null) {
        String key = "DEADLINE:" + a.id();
        if (open && !a.deadline().isBefore(now)) {
          desired.put(key, timed(key, "Deadline: " + a.position() + " at " + a.company(), a.deadline(), DEADLINE_LENGTH, "Application deadline. Managed in DOCKET: " + link));
        } else if (a.deadline().isBefore(now)) keep.add(key);
      }
      if (prefs.followUps() && a.followUpDate() != null) {
        String key = "FOLLOWUP_APP:" + a.id();
        if (open && !a.followUpDate().isBefore(today)) {
          desired.put(key, allDay(key, "Follow up: " + a.company() + " \u2014 " + a.position(), a.followUpDate(), "Follow-up reminder. Managed in DOCKET: " + link));
        } else if (a.followUpDate().isBefore(today)) keep.add(key);
      }
    }
    for (InterviewRow i : interviews) {
      AppRow a = byId.get(i.applicationId());
      if (a == null) continue; // application deleted: not desired, not kept -> stale
      boolean open = !CLOSED.contains(a.status());
      String link = appUrlBase + "/app/applications/" + a.id();
      if (prefs.interviews()) {
        String key = "INTERVIEW:" + i.id();
        if (open && !i.at().isBefore(now)) {
          StringBuilder d = new StringBuilder("Interview round: ").append(i.round());
          if (i.mode() != null && !i.mode().isBlank()) d.append("\nMode: ").append(i.mode());
          if (i.meetingLink() != null && !i.meetingLink().isBlank()) d.append("\nMeeting link: ").append(i.meetingLink());
          d.append("\nManaged in DOCKET: ").append(link);
          desired.put(key, timed(key, "Interview (" + i.round() + "): " + a.company() + " \u2014 " + a.position(), i.at(), INTERVIEW_LENGTH, d.toString()));
        } else if (i.at().isBefore(now)) keep.add(key);
      }
      if (prefs.followUps() && i.followUpDate() != null) {
        String key = "FOLLOWUP_INT:" + i.id();
        if (open && !i.followUpDate().isBefore(today)) {
          desired.put(key, allDay(key, "Follow up after " + i.round() + " interview: " + a.company(), i.followUpDate(), "Interview follow-up. Managed in DOCKET: " + link));
        } else if (i.followUpDate().isBefore(today)) keep.add(key);
      }
    }
    if (desired.size() > MAX_EVENTS) {
      Map<String, Desired> capped = new LinkedHashMap<>();
      for (Map.Entry<String, Desired> e : desired.entrySet()) { if (capped.size() >= MAX_EVENTS) break; capped.put(e.getKey(), e.getValue()); }
      return new Plan(capped, keep);
    }
    return new Plan(desired, keep);
  }

  private static Desired timed(String key, String summary, Instant start, Duration len, String description) {
    Map<String, Object> b = base(key, summary, description);
    b.put("start", Map.of("dateTime", start.toString(), "timeZone", "UTC"));
    b.put("end", Map.of("dateTime", start.plus(len).toString(), "timeZone", "UTC"));
    return new Desired(key, b, sha256(MiniJson.write(b)));
  }

  private static Desired allDay(String key, String summary, LocalDate day, String description) {
    Map<String, Object> b = base(key, summary, description);
    b.put("start", Map.of("date", day.toString()));
    b.put("end", Map.of("date", day.plusDays(1).toString())); // Google all-day end date is exclusive
    return new Desired(key, b, sha256(MiniJson.write(b)));
  }

  private static Map<String, Object> base(String key, String summary, String description) {
    Map<String, Object> b = new LinkedHashMap<>();
    b.put("summary", summary.length() > 200 ? summary.substring(0, 200) : summary);
    b.put("description", description);
    b.put("extendedProperties", Map.of("private", Map.of("docketKey", key)));
    return b;
  }

  /**
   * Client-chosen Google event id: lets a retried create be recognised (409) instead of duplicating the event.
   * Google requires 5-1024 chars from [a-v0-9] (base32hex); lowercase hex qualifies.
   */
  public static String eventId(long userId, String key) { return "dkt" + sha256(userId + ":" + key).substring(0, 40); }

  public static String sha256(String s) {
    try { return java.util.HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(s.getBytes(StandardCharsets.UTF_8))); }
    catch (java.security.NoSuchAlgorithmException e) { throw new IllegalStateException(e); }
  }

  public static List<String> sortedKeys(Plan p) { return new ArrayList<>(p.desired().keySet()); }
}
