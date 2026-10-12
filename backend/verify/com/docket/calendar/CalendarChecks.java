package com.docket.calendar;

import com.docket.calendar.CalendarPlanner.*;
import com.docket.util.MiniJson;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import java.io.OutputStream;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.LocalDate;
import java.util.*;

public class CalendarChecks {
  static int pass = 0, fail = 0;
  static void check(String name, boolean ok) { if (ok) pass++; else { fail++; System.out.println("FAIL: " + name); } }

  public static void main(String[] a) throws Exception {
    crypto(); planner(); engine();
    System.out.println("calendar checks: " + pass + " passed, " + fail + " failed");
    if (fail > 0) System.exit(1);
  }

  static void crypto() {
    String key = Base64.getEncoder().encodeToString(new byte[32]);
    TokenCrypto c = new TokenCrypto(key);
    String enc = c.encrypt("1//refresh-token-value");
    check("encrypts to prefixed text", enc.startsWith("v1.") && !enc.contains("refresh-token-value"));
    check("round trip", "1//refresh-token-value".equals(c.decrypt(enc)));
    check("random IV: same input differs", !enc.equals(c.encrypt("1//refresh-token-value")));
    String tampered = enc.substring(0, enc.length() - 2) + (enc.endsWith("AA") ? "BB" : "AA");
    try { c.decrypt(tampered); check("tamper detected", false); } catch (IllegalArgumentException e) { check("tamper detected", true); }
    byte[] other = new byte[32]; other[0] = 1;
    try { new TokenCrypto(Base64.getEncoder().encodeToString(other)).decrypt(enc); check("wrong key rejected", false); } catch (IllegalArgumentException e) { check("wrong key rejected", true); }
    check("short key invalid", !TokenCrypto.isValidKey(Base64.getEncoder().encodeToString(new byte[16])));
    check("garbage key invalid", !TokenCrypto.isValidKey("%%%"));
    check("blank key invalid", !TokenCrypto.isValidKey(""));
    check("good key valid", TokenCrypto.isValidKey(key));
    try { c.decrypt("plain-text"); check("unprefixed rejected", false); } catch (IllegalArgumentException e) { check("unprefixed rejected", true); }
  }

  static final Instant NOW = Instant.parse("2026-10-10T08:00:00Z");
  static final Prefs ALL = new Prefs(true, true, true);

  static void planner() {
    List<AppRow> apps = List.of(
        new AppRow(1, "Acme", "Intern", "Applied", Instant.parse("2026-10-20T12:00:00Z"), LocalDate.parse("2026-10-15")),
        new AppRow(2, "Globex", "Analyst", "Rejected", Instant.parse("2026-10-25T12:00:00Z"), LocalDate.parse("2026-10-16")),
        new AppRow(3, "Initech", "Dev", "Interview", Instant.parse("2026-10-01T12:00:00Z"), LocalDate.parse("2026-10-02")));
    List<InterviewRow> ints = List.of(
        new InterviewRow(10, 1, Instant.parse("2026-10-12T09:30:00Z"), "Technical", "Online", "https://meet.example/x", LocalDate.parse("2026-10-14")),
        new InterviewRow(11, 3, Instant.parse("2026-10-05T09:30:00Z"), "HR", "Online", null, null),
        new InterviewRow(12, 2, Instant.parse("2026-10-13T09:30:00Z"), "HR", null, null, null),
        new InterviewRow(13, 99, Instant.parse("2026-10-13T09:30:00Z"), "HR", null, null, null));
    Plan p = CalendarPlanner.plan(apps, ints, ALL, NOW, "https://app.example");
    Set<String> d = p.desired().keySet();
    check("open app deadline planned", d.contains("DEADLINE:1"));
    check("open app follow-up planned", d.contains("FOLLOWUP_APP:1"));
    check("future interview planned", d.contains("INTERVIEW:10"));
    check("interview follow-up planned", d.contains("FOLLOWUP_INT:10"));
    check("closed app deadline NOT planned", !d.contains("DEADLINE:2"));
    check("closed app follow-up NOT planned", !d.contains("FOLLOWUP_APP:2"));
    check("closed app future interview NOT planned", !d.contains("INTERVIEW:12"));
    check("interview of unknown application ignored", !d.contains("INTERVIEW:13") && !p.keep().contains("INTERVIEW:13"));
    check("past deadline kept, not planned", p.keep().contains("DEADLINE:3") && !d.contains("DEADLINE:3"));
    check("past follow-up kept", p.keep().contains("FOLLOWUP_APP:3"));
    check("past interview kept", p.keep().contains("INTERVIEW:11"));
    Desired iv = p.desired().get("INTERVIEW:10");
    String json = MiniJson.write(iv.body());
    check("interview body has link and docket url", json.contains("https://meet.example/x") && json.contains("https://app.example/app/applications/1"));
    check("interview is one hour", json.contains("2026-10-12T09:30:00Z") && json.contains("2026-10-12T10:30:00Z"));
    check("all-day end is exclusive next day", MiniJson.write(p.desired().get("FOLLOWUP_APP:1").body()).contains("\"end\":{\"date\":\"2026-10-16\"}"));
    check("tagged with docket key", json.contains("\"docketKey\":\"INTERVIEW:10\""));
    check("hash stable", iv.hash().equals(CalendarPlanner.plan(apps, ints, ALL, NOW, "https://app.example").desired().get("INTERVIEW:10").hash()));
    check("hash changes with content", !iv.hash().equals(CalendarPlanner.plan(apps, List.of(new InterviewRow(10, 1, Instant.parse("2026-10-12T10:30:00Z"), "Technical", "Online", "https://meet.example/x", null)), ALL, NOW, "https://app.example").desired().get("INTERVIEW:10").hash()));
    Plan none = CalendarPlanner.plan(apps, ints, new Prefs(false, false, false), NOW, "https://app.example");
    check("all prefs off plans nothing and keeps nothing", none.desired().isEmpty() && none.keep().isEmpty());
    Plan onlyDeadlines = CalendarPlanner.plan(apps, ints, new Prefs(false, false, true), NOW, "https://app.example");
    check("only deadlines pref", onlyDeadlines.desired().keySet().equals(Set.of("DEADLINE:1")));
    String id = CalendarPlanner.eventId(7, "INTERVIEW:10");
    check("event id is valid base32hex and long enough", id.matches("[a-v0-9]{5,1024}") && id.length() == 43);
    check("event id differs per user", !id.equals(CalendarPlanner.eventId(8, "INTERVIEW:10")));
    check("event id deterministic", id.equals(CalendarPlanner.eventId(7, "INTERVIEW:10")));
  }

  // ---- fake Google ----
  static final Map<String, String> EVENTS = new LinkedHashMap<>();   // id -> json
  static final List<String> CALLS = new ArrayList<>();
  static volatile int forceStatus = 0;
  static volatile String forceReason = null;
  static volatile boolean refreshRevoked = false;

  static void reply(HttpExchange ex, int code, String body) throws java.io.IOException {
    byte[] b = body.getBytes(StandardCharsets.UTF_8);
    ex.getResponseHeaders().add("Content-Type", "application/json");
    if (b.length == 0) ex.sendResponseHeaders(code, -1); else { ex.sendResponseHeaders(code, b.length); try (OutputStream o = ex.getResponseBody()) { o.write(b); } }
    ex.close();
  }

  static void engine() throws Exception {
    HttpServer s = HttpServer.create(new InetSocketAddress(InetAddress.getLoopbackAddress(), 0), 0);
    s.createContext("/token", ex -> {
      String form = new String(ex.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
      if (form.contains("grant_type=refresh_token")) {
        if (refreshRevoked) { reply(ex, 400, "{\"error\":\"invalid_grant\"}"); return; }
        reply(ex, 200, "{\"access_token\":\"AT1\",\"expires_in\":3599}");
      } else if (form.contains("code=goodcode")) reply(ex, 200, "{\"access_token\":\"AT0\",\"refresh_token\":\"RT0\",\"scope\":\"https://www.googleapis.com/auth/calendar.events\"}");
      else reply(ex, 400, "{\"error\":\"invalid_grant\"}");
    });
    s.createContext("/revoke", ex -> { CALLS.add("REVOKE"); reply(ex, 200, "{}"); });
    s.createContext("/calendar/v3/calendars/primary/events", ex -> {
      String m = ex.getRequestMethod(); String path = ex.getRequestURI().getPath();
      String auth = ex.getRequestHeaders().getFirst("Authorization");
      if (!"Bearer AT1".equals(auth)) { reply(ex, 401, "{}"); return; }
      String body = new String(ex.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
      if (forceStatus != 0) { reply(ex, forceStatus, "{\"error\":{\"errors\":[{\"reason\":\"" + forceReason + "\"}]}}"); return; }
      String id = path.substring(path.lastIndexOf('/') + 1);
      if (m.equals("POST")) {
        Map<String, Object> j = MiniJson.asMap(MiniJson.parse(body)); String nid = MiniJson.str(j.get("id"));
        CALLS.add("INSERT " + nid);
        if (EVENTS.containsKey(nid)) { reply(ex, 409, "{\"error\":{\"errors\":[{\"reason\":\"duplicate\"}]}}"); return; }
        EVENTS.put(nid, body); reply(ex, 200, body);
      } else if (m.equals("PUT")) {
        CALLS.add("UPDATE " + id);
        if (!EVENTS.containsKey(id)) { reply(ex, 404, "{}"); return; }
        EVENTS.put(id, body); reply(ex, 200, body);
      } else if (m.equals("DELETE")) {
        CALLS.add("DELETE " + id);
        if (EVENTS.remove(id) == null) { reply(ex, 410, "{}"); return; }
        reply(ex, 204, "");
      }
    });
    s.start();
    String base = "http://127.0.0.1:" + s.getAddress().getPort();
    GoogleClient g = new GoogleClient(new GoogleClient.Config("cid", "secret", "http://localhost:8080/cb", "https://accounts.example/auth", base + "/token", base + "/revoke", base + "/calendar/v3"));
    try {
      String url = g.authorizationUrl("STATE123");
      check("auth url has offline access and consent", url.contains("access_type=offline") && url.contains("prompt=consent") && url.contains("state=STATE123"));
      check("auth url requests only calendar.events", url.contains("calendar.events") && !url.contains("auth%2Fcalendar&") && !url.contains("userinfo"));
      check("auth url has no client secret", !url.contains("secret"));
      GoogleClient.Tokens t = g.exchangeCode("goodcode");
      check("code exchange", "AT0".equals(t.accessToken()) && "RT0".equals(t.refreshToken()) && t.scope().contains("calendar.events"));
      try { g.exchangeCode("bad"); check("bad code rejected", false); } catch (GoogleClient.AuthException e) { check("bad code rejected", e.grantRevoked()); }
      check("refresh gives access token", "AT1".equals(g.refreshAccessToken("RT0")));
      refreshRevoked = true;
      try { g.refreshAccessToken("RT0"); check("revoked grant detected", false); } catch (GoogleClient.AuthException e) { check("revoked grant detected", e.grantRevoked()); }
      refreshRevoked = false;
      check("revoke accepted", g.revoke("RT0") && CALLS.contains("REVOKE"));

      List<AppRow> apps = new ArrayList<>(List.of(new AppRow(1, "Acme", "Intern", "Applied", Instant.parse("2026-10-20T12:00:00Z"), null)));
      List<InterviewRow> ints = new ArrayList<>(List.of(new InterviewRow(10, 1, Instant.parse("2026-10-12T09:30:00Z"), "Technical", "Online", null, null)));
      Map<String, SyncEngine.Mapped> mapped = new LinkedHashMap<>();
      SyncEngine.Store store = new SyncEngine.Store() {
        public void put(String k, String id, String h) { mapped.put(k, new SyncEngine.Mapped(id, h)); }
        public void remove(String k) { mapped.remove(k); }
      };
      CALLS.clear();
      SyncEngine.Outcome o1 = SyncEngine.run(7, "AT1", CalendarPlanner.plan(apps, ints, ALL, NOW, "https://app"), mapped, g, store);
      check("first sync creates 2", o1.created() == 2 && o1.status().equals("OK") && EVENTS.size() == 2 && mapped.size() == 2);
      CALLS.clear();
      SyncEngine.Outcome o2 = SyncEngine.run(7, "AT1", CalendarPlanner.plan(apps, ints, ALL, NOW, "https://app"), mapped, g, store);
      check("second sync changes nothing and makes no calls", o2.unchanged() == 2 && o2.created() + o2.updated() + o2.deleted() == 0 && CALLS.isEmpty());
      ints.set(0, new InterviewRow(10, 1, Instant.parse("2026-10-12T11:00:00Z"), "Technical", "Online", null, null));
      CALLS.clear();
      SyncEngine.Outcome o3 = SyncEngine.run(7, "AT1", CalendarPlanner.plan(apps, ints, ALL, NOW, "https://app"), mapped, g, store);
      check("changed interview time updates only that event", o3.updated() == 1 && CALLS.size() == 1 && CALLS.get(0).startsWith("UPDATE"));
      // lost mapping (e.g. crash after Google accepted): 409 adopts the existing event, no duplicate
      mapped.clear(); CALLS.clear(); int before = EVENTS.size();
      SyncEngine.Outcome o4 = SyncEngine.run(7, "AT1", CalendarPlanner.plan(apps, ints, ALL, NOW, "https://app"), mapped, g, store);
      check("retry after lost mapping does not duplicate", EVENTS.size() == before && mapped.size() == 2 && o4.failed() == 0 && o4.updated() == 2);
      // event purged in Google: update 404 -> recreate
      String someId = mapped.get("DEADLINE:1").eventId(); EVENTS.remove(someId);
      ints.clear();
      CALLS.clear();
      SyncEngine.Outcome o5 = SyncEngine.run(7, "AT1", CalendarPlanner.plan(apps, ints, ALL, NOW, "https://app"), new LinkedHashMap<>(mapped) {{ put("DEADLINE:1", new SyncEngine.Mapped(someId, "stale-hash")); }}, g, store);
      check("purged event recreated", o5.created() == 1 && EVENTS.containsKey(someId));
      check("removed interview deleted in Google", o5.deleted() == 1 && !mapped.containsKey("INTERVIEW:10"));
      // application deleted entirely
      CALLS.clear();
      SyncEngine.Outcome o6 = SyncEngine.run(7, "AT1", CalendarPlanner.plan(List.of(), List.of(), ALL, NOW, "https://app"), mapped, g, store);
      check("everything removed when sources gone", o6.deleted() >= 1 && mapped.isEmpty() && EVENTS.isEmpty());
      // rate limit stops the run and is not reported as OK
      forceStatus = 403; forceReason = "rateLimitExceeded";
      SyncEngine.Outcome o7 = SyncEngine.run(7, "AT1", CalendarPlanner.plan(apps, List.of(new InterviewRow(10, 1, Instant.parse("2026-10-12T11:00:00Z"), "Technical", "Online", null, null)), ALL, NOW, "https://app"), mapped, g, store);
      check("rate limit stops, never OK", o7.stopReason() != null && !o7.status().equals("OK") && o7.created() == 0 && mapped.isEmpty());
      forceStatus = 500; forceReason = "backendError";
      SyncEngine.Outcome o8 = SyncEngine.run(7, "AT1", CalendarPlanner.plan(apps, List.of(), ALL, NOW, "https://app"), mapped, g, store);
      check("server error counted as failure, nothing recorded", o8.failed() >= 1 && o8.status().equals("FAILED") && mapped.isEmpty());
      forceStatus = 0;
      SyncEngine.Outcome o9 = SyncEngine.run(7, "BAD", CalendarPlanner.plan(apps, List.of(), ALL, NOW, "https://app"), mapped, g, store);
      check("401 stops with reconnect hint", o9.stopReason() != null && o9.stopReason().contains("Reconnect") && o9.status().equals("FAILED"));
      check("outcome note is readable", o1.note().contains("2 added"));
    } finally { s.stop(0); }
  }
}
