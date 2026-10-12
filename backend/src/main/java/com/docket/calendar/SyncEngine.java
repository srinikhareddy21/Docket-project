package com.docket.calendar;

import com.docket.calendar.CalendarPlanner.Desired;
import com.docket.calendar.CalendarPlanner.Plan;
import java.util.Map;

/**
 * Applies a Plan to Google Calendar: create, update only when content changed, delete what no longer applies.
 * Idempotent: creates use a deterministic event id, so a retry after a timeout adopts the existing event (409)
 * instead of duplicating it. Never reports success for a failed call. Pure logic.
 */
public final class SyncEngine {
  private SyncEngine() {}

  public record Mapped(String eventId, String hash) {}

  public interface Store {
    void put(String key, String eventId, String hash);
    void remove(String key);
  }

  public record Outcome(int created, int updated, int deleted, int unchanged, int failed, String stopReason) {
    public String status() {
      int done = created + updated + deleted;
      if (failed == 0 && stopReason == null) return "OK";
      return done > 0 ? "PARTIAL" : "FAILED";
    }
    public String note() {
      StringBuilder b = new StringBuilder();
      b.append(created).append(" added, ").append(updated).append(" updated, ").append(deleted).append(" removed");
      if (failed > 0) b.append(", ").append(failed).append(" failed");
      if (stopReason != null) b.append(". ").append(stopReason);
      return b.toString();
    }
  }

  public static Outcome run(long userId, String accessToken, Plan plan, Map<String, Mapped> mapped, GoogleClient g, Store store) {
    int created = 0, updated = 0, deleted = 0, unchanged = 0, failed = 0;
    String stop = null;

    for (Desired d : plan.desired().values()) {
      Mapped m = mapped.get(d.key());
      String id = m != null ? m.eventId() : CalendarPlanner.eventId(userId, d.key());
      if (m != null && m.hash().equals(d.hash())) { unchanged++; continue; }
      GoogleClient.Result r;
      boolean wasCreate = false;
      if (m == null) {
        r = g.insert(accessToken, id, d.body());
        wasCreate = true;
        if (r.status() == 409) { r = g.update(accessToken, id, d.body()); wasCreate = false; } // already there from an earlier attempt
      } else {
        r = g.update(accessToken, id, d.body());
        if (r.status() == 404) { r = g.insert(accessToken, id, d.body()); wasCreate = true; }   // removed in Google for good
      }
      if (r.ok()) { store.put(d.key(), id, d.hash()); if (wasCreate) created++; else updated++; continue; }
      failed++;
      if ((stop = fatal(r)) != null) return new Outcome(created, updated, deleted, unchanged, failed, stop);
    }

    for (Map.Entry<String, Mapped> e : mapped.entrySet()) {
      String key = e.getKey();
      if (plan.desired().containsKey(key) || plan.keep().contains(key)) continue;
      GoogleClient.Result r = g.delete(accessToken, e.getValue().eventId());
      if (r.ok() || r.status() == 404 || r.status() == 410) { store.remove(key); deleted++; continue; }
      failed++;
      if ((stop = fatal(r)) != null) return new Outcome(created, updated, deleted, unchanged, failed, stop);
    }
    return new Outcome(created, updated, deleted, unchanged, failed, stop);
  }

  private static String fatal(GoogleClient.Result r) {
    if (r.rateLimited()) return "Google asked us to slow down. The rest will sync on the next run.";
    if (r.status() == 401) return "Google rejected the access token. Reconnect Google Calendar if this continues.";
    if (r.status() == 403) return "Google denied the request (" + (r.reason() == null ? "forbidden" : r.reason()) + "). Check that Calendar access was granted.";
    if (r.status() == 0) return "Could not reach Google. The rest will sync on the next run.";
    return null;
  }
}
