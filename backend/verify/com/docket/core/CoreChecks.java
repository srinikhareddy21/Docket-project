package com.docket.core;

import com.docket.applications.Csv;
import com.docket.applications.Normalizer;
import com.docket.applications.Priority;
import com.docket.auth.PasswordPolicy;
import com.docket.dashboard.Metrics;
import com.docket.dashboard.Metrics.*;
import com.docket.documents.FileSniffer;
import com.docket.reminders.ReminderPlanner;
import com.docket.notifications.NotificationPlanner;
import com.docket.security.SlidingWindowLimiter;
import com.docket.skills.SkillMatcher;
import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.*;
import java.util.*;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

/** Plain-Java checks for the pure business rules (Phase 8). No Spring, no database. */
public class CoreChecks {
  static int pass = 0, fail = 0;
  static void check(String name, boolean ok) { if (ok) pass++; else { fail++; System.out.println("FAIL: " + name); } }

  static final Instant NOW = Instant.parse("2026-03-11T12:00:00Z");           // a Wednesday
  static final LocalDate TODAY = LocalDate.of(2026, 3, 11);
  static Instant h(long hours) { return NOW.plusSeconds(hours * 3600); }

  public static void main(String[] a) throws Exception {
    priority(); normalizer(); csv(); skills(); files(); reminders(); notifications(); passwords(); limiter(); metrics(); isolationOfNewAccount();
    System.out.println("core checks: " + pass + " passed, " + fail + " failed");
    if (fail > 0) System.exit(1);
  }

  static Priority.Result p(String status, Instant deadline, LocalDate fu, Instant changed, Instant iv, boolean prep) {
    return Priority.compute(status, deadline, fu, changed, iv, prep, NOW, TODAY);
  }

  static void priority() {
    for (String s : List.of("Selected", "Rejected", "Withdrawn"))
      check("closed " + s + " scores 0", p(s, h(1), TODAY.minusDays(3), null, h(2), true).score() == 0);
    check("deadline passed = 50", p("Applied", h(-1), null, null, null, false).score() == 50);
    check("deadline 47h = 55", p("Applied", h(47), null, null, null, false).score() == 55);
    check("deadline exactly 48h = 55", p("Applied", h(48), null, null, null, false).score() == 55);
    check("deadline 6d = 30", p("Applied", h(24 * 6), null, null, null, false).score() == 30);
    check("deadline 13d = 15", p("Applied", h(24 * 13), null, null, null, false).score() == 15);
    check("deadline 30d = 0", p("Applied", h(24 * 30), null, null, null, false).score() == 0);
    check("overdue follow-up = 35", p("Applied", null, TODAY.minusDays(1), null, null, false).score() == 35);
    check("follow-up today = 25", p("Applied", null, TODAY, null, null, false).score() == 25);
    check("follow-up tomorrow = 0", p("Applied", null, TODAY.plusDays(1), null, null, false).score() == 0);
    check("interview within 48h = 50 (High)", p("Interview", null, null, null, h(10), false).score() == 50
        && p("Interview", null, null, null, h(10), false).level().equals("High"));
    check("interview tomorrow alone is High", p("Interview", null, null, null, h(24), false).level().equals("High"));
    check("deadline tomorrow alone is High", p("Applied", h(24), null, null, null, false).level().equals("High"));
    check("interview within 7d with incomplete prep = 30+15", p("Interview", null, null, null, h(24 * 3), true).score() == 45);
    check("interview in 20d ignores prep gap = 10", p("Interview", null, null, null, h(24 * 20), true).score() == 10);
    Priority.Result stale = p("Applied", null, null, h(-24 * 365), null, false);
    check("an old application alone is only 5 and Low", stale.score() == 5 && stale.level().equals("Low"));
    check("staleness only for Applied/Shortlisted", p("Assessment", null, null, h(-24 * 365), null, false).score() == 0);
    Priority.Result hi = p("Applied", h(10), TODAY.minusDays(2), null, null, false);
    check("deadline<48h + overdue follow-up is High", hi.score() == 90 && hi.level().equals("High") && hi.reasons().size() == 2);
    check("score is capped at 100", p("Interview", h(-5), TODAY.minusDays(1), h(-24 * 99), h(5), true).score() <= 100);
    check("quiet application explains itself", p("Applied", null, null, null, null, false).reasons().equals(List.of("Nothing time-sensitive right now.")));
    check("level boundaries: 15 Low, 30 Medium, 50 High, 55 High",
        p("Applied", h(24 * 13), null, null, null, false).level().equals("Low")                         // 15
        && p("Applied", h(24 * 6), null, null, null, false).score() == 30                                 // 30
        && p("Applied", h(24 * 6), null, null, null, false).level().equals("Medium")
        && p("Applied", h(-1), null, null, null, false).score() == 50                                    // 50 overdue
        && p("Applied", h(-1), null, null, null, false).level().equals("High")
        && p("Applied", h(47), null, null, null, false).score() == 55                                    // 55 within 48h
        && p("Applied", h(47), null, null, null, false).level().equals("High"));
  }

  static void normalizer() {
    check("company: case, punctuation, accents", Normalizer.text("  Acme, Inc. ").equals(Normalizer.text("ACME inc")));
    check("company: accents stripped", Normalizer.text("Café Société").equals("cafe societe"));
    check("company: null safe", Normalizer.text(null).isEmpty());
    String base = Normalizer.url("https://jobs.example.com/careers/123");
    check("url: www, scheme, fragment, trailing slash ignored",
        base.equals(Normalizer.url("http://WWW.jobs.example.com/careers/123/#apply")) && base.equals("jobs.example.com/careers/123"));
    check("url: tracking params dropped", base.equals(Normalizer.url("https://jobs.example.com/careers/123?utm_source=x&utm_medium=y&gclid=1&ref=abc")));
    check("url: job-identifying params kept and sorted",
        Normalizer.url("https://x.com/j?id=5&b=2").equals(Normalizer.url("https://x.com/j?b=2&id=5")) && Normalizer.url("https://x.com/j?id=5").endsWith("?id=5"));
    check("url: different job ids stay different", !Normalizer.url("https://x.com/j?id=5").equals(Normalizer.url("https://x.com/j?id=6")));
    check("url: non-http rejected", Normalizer.url("javascript:alert(1)").isEmpty() && Normalizer.url("ftp://x.com/a").isEmpty() && Normalizer.url("not a url").isEmpty());
    check("urlKey is stable 64-hex", Normalizer.urlKey("https://a.com/x").length() == 64 && Normalizer.urlKey("https://a.com/x").equals(Normalizer.urlKey("http://www.a.com/x/")));
    check("urlKey empty for junk", Normalizer.urlKey("").isEmpty() && Normalizer.urlKey(null).isEmpty());
    check("LIKE wildcards are escaped", Normalizer.likePattern("100%_off!").equals("%100!%!_off!!%") && Normalizer.likePattern("  ").isEmpty());
  }

  static void csv() {
    check("plain cell", Csv.cell("Acme").equals("Acme"));
    check("comma quoted", Csv.cell("Acme, Inc").equals("\"Acme, Inc\""));
    check("quote doubled", Csv.cell("say \"hi\"").equals("\"say \"\"hi\"\"\""));
    check("newline quoted", Csv.cell("a\nb").equals("\"a\nb\""));
    for (String bad : List.of("=1+1", "+1", "-1", "@SUM(A1)", "\tx"))
      check("formula injection neutralized: " + bad.trim(), Csv.cell(bad).startsWith("'") || Csv.cell(bad).startsWith("\"'"));
    check("null is empty", Csv.cell(null).isEmpty());
    check("row ends with CRLF", Csv.row(List.of("a", "b,c")).equals("a,\"b,c\"\r\n"));
    byte[] b = Csv.bytes("x");
    check("BOM prefix", b.length == 4 && (b[0] & 0xFF) == 0xEF && (b[1] & 0xFF) == 0xBB && (b[2] & 0xFF) == 0xBF);
  }

  static void skills() {
    List<String> req = new ArrayList<>(), have = new ArrayList<>();
    for (int i = 0; i < 11; i++) req.add("skill" + i);
    for (int i = 0; i < 8; i++) have.add("SKILL" + i + " ");
    SkillMatcher.Result r = SkillMatcher.match(have, req);
    check("8 of 11 = 72.7%", Math.abs(r.score() - 72.727) < 0.01 && r.matched().size() == 8 && r.missing().size() == 3 && r.total() == 11);
    check("no required skills -> null, not 0", SkillMatcher.score(List.of("java"), List.of()) == null && SkillMatcher.score(List.of("java"), List.of(" ", "")) == null);
    check("no user skills -> 0", SkillMatcher.score(List.of(), List.of("java")) == 0.0);
    check("alias js=javascript", SkillMatcher.score(List.of("JavaScript"), List.of("JS")) == 100.0);
    check("duplicates after normalization count once", SkillMatcher.match(List.of("react"), List.of("React", "reactjs", "REACT.JS")).total() == 1);
    check("100% when all matched", SkillMatcher.score(List.of("a", "b"), List.of("a", "b")) == 100.0);
  }

  static byte[] zip(String... names) throws Exception {
    ByteArrayOutputStream bo = new ByteArrayOutputStream();
    try (ZipOutputStream z = new ZipOutputStream(bo)) { for (String n : names) { z.putNextEntry(new ZipEntry(n)); z.write(1); z.closeEntry(); } }
    return bo.toByteArray();
  }
  static FileSniffer.Kind sniff(byte[] data) throws Exception {
    Path f = Files.createTempFile("docket-sniff", ".bin");
    try { Files.write(f, data); return FileSniffer.detect(f); } finally { Files.deleteIfExists(f); }
  }
  static byte[] concat(byte[] a, int pad) { byte[] o = Arrays.copyOf(a, a.length + pad); return o; }

  static void files() throws Exception {
    check("PDF by bytes", sniff("%PDF-1.7\n...".getBytes(StandardCharsets.US_ASCII)) == FileSniffer.Kind.PDF);
    check("PNG by bytes", sniff(concat(new byte[]{(byte) 0x89, 'P', 'N', 'G', 0x0D, 0x0A, 0x1A, 0x0A}, 8)) == FileSniffer.Kind.PNG);
    check("JPEG by bytes", sniff(concat(new byte[]{(byte) 0xFF, (byte) 0xD8, (byte) 0xFF, (byte) 0xE0}, 8)) == FileSniffer.Kind.JPEG);
    check("legacy .doc by bytes", sniff(concat(new byte[]{(byte) 0xD0, (byte) 0xCF, 0x11, (byte) 0xE0, (byte) 0xA1, (byte) 0xB1, 0x1A, (byte) 0xE1}, 8)) == FileSniffer.Kind.DOC);
    check("real docx zip accepted", sniff(zip("[Content_Types].xml", "word/document.xml")) == FileSniffer.Kind.DOCX);
    check("generic zip rejected", sniff(zip("a.txt", "b.txt")) == null);
    check("zip missing word/document.xml rejected", sniff(zip("[Content_Types].xml")) == null);
    check("executable rejected", sniff(concat(new byte[]{'M', 'Z', (byte) 0x90, 0}, 16)) == null);
    check("script rejected", sniff("#!/bin/sh\nrm -rf /".getBytes(StandardCharsets.US_ASCII)) == null);
    check("html rejected", sniff("<html><script>alert(1)</script>".getBytes(StandardCharsets.US_ASCII)) == null);
    check("empty file rejected", sniff(new byte[0]) == null);
    check("extension must match type", FileSniffer.Kind.PDF.allowsExtension("PDF") && !FileSniffer.Kind.PDF.allowsExtension("exe") && FileSniffer.Kind.JPEG.allowsExtension("jpeg"));
    check("display name strips path", FileSniffer.displayName("../../etc/passwd").equals("passwd") && FileSniffer.displayName("C:\\x\\cv.pdf").equals("cv.pdf"));
    check("display name strips control and bidi chars", FileSniffer.displayName("cv\u202Efdp.exe").equals("cvfdp.exe") && !FileSniffer.displayName("a\nb.pdf").contains("\n"));
    check("display name bounded and keeps extension", FileSniffer.displayName("a".repeat(400) + ".pdf").length() <= 150 && FileSniffer.displayName("a".repeat(400) + ".pdf").endsWith(".pdf"));
    check("empty display name falls back", FileSniffer.displayName("").equals("document") && FileSniffer.displayName(null).equals("document"));
  }

  static void reminders() {
    ReminderPlanner.Prefs prefs = new ReminderPlanner.Prefs(true, 24, 24, true, ZoneId.of("Asia/Kolkata"));
    ReminderPlanner.PlanApp open = new ReminderPlanner.PlanApp(1, "Acme", "Intern", true, h(48), TODAY);
    ReminderPlanner.PlanApp closed = new ReminderPlanner.PlanApp(2, "Beta", "Dev", false, h(48), TODAY);
    List<ReminderPlanner.Planned> out = ReminderPlanner.plan(List.of(open, closed), List.of(), prefs, NOW);
    check("closed applications never get reminders", out.stream().noneMatch(x -> x.appId() == 2));
    ReminderPlanner.Planned dl = out.stream().filter(x -> x.type().equals("DEADLINE")).findFirst().orElseThrow();
    check("deadline reminder is lead-hours before", dl.remindAt().equals(h(48).minusSeconds(24 * 3600)));
    check("follow-up planned at 09:00 local on the day", out.stream().anyMatch(x -> x.type().equals("FOLLOW_UP")
        && x.remindAt().atZone(ZoneId.of("Asia/Kolkata")).toLocalTime().equals(LocalTime.of(9, 0))));
    check("past deadline not planned", ReminderPlanner.plan(List.of(new ReminderPlanner.PlanApp(3, "C", "R", true, h(-1), null)), List.of(), prefs, NOW).isEmpty());
    check("beyond 30-day horizon not planned", ReminderPlanner.plan(List.of(new ReminderPlanner.PlanApp(3, "C", "R", true, h(24 * 31), null)), List.of(), prefs, NOW).isEmpty());
    check("follow-up disabled -> none", ReminderPlanner.plan(List.of(new ReminderPlanner.PlanApp(4, "C", "R", true, null, TODAY)), List.of(), new ReminderPlanner.Prefs(true, 24, 24, false, ZoneOffset.UTC), NOW).isEmpty());
    check("follow-up day already over -> none", ReminderPlanner.plan(List.of(new ReminderPlanner.PlanApp(4, "C", "R", true, null, TODAY.minusDays(2))), List.of(), prefs, NOW).isEmpty());
    ReminderPlanner.PlanInterview iv = new ReminderPlanner.PlanInterview(9, 1, h(30), "Technical", null);
    List<ReminderPlanner.Planned> withIv = ReminderPlanner.plan(List.of(open), List.of(iv, new ReminderPlanner.PlanInterview(10, 2, h(30), "HR", null)), prefs, NOW);
    check("interview for open app planned, for closed app skipped", withIv.stream().filter(x -> x.type().equals("INTERVIEW")).count() == 1);
    Set<String> keys = new HashSet<>(); boolean unique = true;
    for (ReminderPlanner.Planned x : ReminderPlanner.plan(List.of(open), List.of(iv), prefs, NOW)) unique &= keys.add(x.dedupKey());
    check("dedup keys are unique within a plan", unique);
    check("same inputs give same keys (no duplicate rows on re-plan)",
        ReminderPlanner.plan(List.of(open), List.of(iv), prefs, NOW).stream().map(ReminderPlanner.Planned::dedupKey).toList()
            .equals(ReminderPlanner.plan(List.of(open), List.of(iv), prefs, NOW.plusSeconds(60)).stream().map(ReminderPlanner.Planned::dedupKey).toList()));
    ReminderPlanner.PlanApp moved = new ReminderPlanner.PlanApp(1, "Acme", "Intern", true, h(72), TODAY);
    check("moving a deadline changes its key (old one retires)",
        !ReminderPlanner.plan(List.of(open), List.of(), prefs, NOW).get(0).dedupKey().equals(ReminderPlanner.plan(List.of(moved), List.of(), prefs, NOW).get(0).dedupKey()));
  }

  static void passwords() {
    check("accepts strong", PasswordPolicy.violation("Str0ng!Passw0rd") == null);
    for (String bad : new String[]{null, "", "Sh0rt!a", "alllowercase1!", "ALLUPPERCASE1!", "NoNumbersHere!", "NoSymbols12345A"})
      check("rejects " + bad, PasswordPolicy.violation(bad) != null);
    check("rejects >72 bytes", PasswordPolicy.violation("Aa1!" + "x".repeat(70)) != null);
    check("multi-byte counted in bytes", PasswordPolicy.violation("Aa1!" + "é".repeat(35)) != null);
  }

  static void limiter() throws Exception {
    SlidingWindowLimiter l = new SlidingWindowLimiter(3, Duration.ofMillis(300));
    check("first 3 allowed", l.tryAcquire("ip") && l.tryAcquire("ip") && l.tryAcquire("ip"));
    check("4th blocked", !l.tryAcquire("ip") && l.isBlocked("ip"));
    check("other keys unaffected", l.tryAcquire("other"));
    Thread.sleep(350);
    check("window expires", !l.isBlocked("ip") && l.tryAcquire("ip"));
    SlidingWindowLimiter f = new SlidingWindowLimiter(2, Duration.ofSeconds(5));
    f.record("a@b.c"); f.record("a@b.c");
    check("recorded failures block", f.isBlocked("a@b.c"));
    f.clear("a@b.c");
    check("clear on success unblocks", !f.isBlocked("a@b.c"));
  }

  static AppRow app(long id, String status, String source, LocalDate on, Instant deadline, LocalDate fu, Double score) {
    return new AppRow(id, "Co" + id, "Role", status, source, on, deadline, fu, h(-24), score);
  }

  static void metrics() {
    // A new account: everything is empty/zero, nothing invented.
    Input empty = new Input(List.of(), List.of(), List.of(), Map.of(), List.of(), NOW, 8);
    Summary s0 = Metrics.summary(empty);
    check("new account: all counts zero", s0.total() == 0 && s0.thisWeek() == 0 && s0.interviewsScheduled() == 0 && s0.offers() == 0 && s0.upcomingDeadlines() == 0 && s0.reachedInterview() == 0);
    check("new account: no actions, no recent, no sources, no trend",
        s0.actions().isEmpty() && s0.recent().isEmpty() && s0.sources().isEmpty() && Metrics.trends(List.of()).isEmpty() && s0.statuses().isEmpty());
    check("new account: funnel stages all zero", s0.funnel().size() == 5 && s0.funnel().stream().allMatch(x -> x.count() == 0));

    LocalDate monday = LocalDate.of(2026, 3, 9);
    List<AppRow> apps = List.of(
        app(1, "Rejected", "LinkedIn", monday, null, null, 50.0),        // rejected AFTER an interview
        app(2, "Selected", "Referral", monday.minusDays(7), null, null, 100.0),
        app(3, "Applied", "LinkedIn", monday.plusDays(1), h(24), null, null),
        app(4, "Applied", null, monday.minusDays(30), null, null, 10.0));
    List<HistRow> hist = List.of(
        new HistRow(1, null, "Applied", h(-500)), new HistRow(1, "Applied", "Interview", h(-400)), new HistRow(1, "Interview", "Rejected", h(-300)),
        new HistRow(2, null, "Applied", h(-500)), new HistRow(2, "Applied", "Selected", h(-100)));
    Input in = new Input(apps, hist, List.of(), Map.of(), List.of(), NOW, 8);
    Summary s = Metrics.summary(in);
    check("total counts all four", s.total() == 4);
    check("this week = Mon-Sun (apps 1 and 3)", s.thisWeek() == 2);
    check("offers = currently Selected", s.offers() == 1);
    check("rejected-after-interview still counted as reached interview", s.reachedInterview() == 2);
    check("funnel is cumulative and non-increasing", s.funnel().get(0).count() == 4 && s.funnel().get(3).count() == 2 && s.funnel().get(4).count() == 1);
    check("funnel never increases down the stages", java.util.stream.IntStream.range(1, 5).allMatch(i -> s.funnel().get(i).count() <= s.funnel().get(i - 1).count()));
    check("upcoming deadline counts open app only", s.upcomingDeadlines() == 1);
    SourceRow li = s.sources().stream().filter(x -> x.source().equals("LinkedIn")).findFirst().orElseThrow();
    check("source row: 2 apps, 1 interview, 0 offers", li.applications() == 2 && li.interviews() == 1 && li.offers() == 0);
    check("missing source grouped as Unspecified", s.sources().stream().anyMatch(x -> x.source().equals(Metrics.UNSPECIFIED_SOURCE)));
    check("offer count <= interview count per source (rate cannot exceed 100%)", s.sources().stream().allMatch(x -> x.offers() <= x.interviews()));
    check("status counts follow workflow order", s.statuses().stream().map(Count::label).toList().equals(List.of("Applied", "Selected", "Rejected")));
    check("skill buckets + 'not enough data' add up", s.skillMatch().stream().mapToInt(Count::count).sum() == 4 && s.skillMatch().get(4).count() == 1);
    check("interview record alone counts as reaching Interview", Metrics.reachedRank(List.of(app(7, "Applied", "X", monday, null, null, null)), List.of(),
        List.of(new IvRow(1, 7, h(5), "HR", "Pending", null, h(-1)))).get(7L) >= 3);

    // Trends: zero-filled weeks between first and last, Monday keys.
    List<Metrics.Period> t = Metrics.trends(apps);
    check("trend weeks are Mondays and zero-filled", t.get(0).period().equals("2026-02-02") && t.get(t.size() - 1).period().equals("2026-03-09")
        && t.stream().mapToInt(Metrics.Period::count).sum() == 4 && t.stream().anyMatch(x -> x.count() == 0));
    check("undated applications are not in the trend", Metrics.trends(List.of(app(9, "Applied", "X", null, null, null, null))).isEmpty());

    // Needs-action: closed apps never appear; overdue first.
    List<AppRow> act = List.of(
        app(1, "Applied", "X", monday, h(-2), null, null),          // deadline passed
        app(2, "Selected", "X", monday, h(-2), TODAY.minusDays(5), null),   // closed: must not appear
        app(3, "Applied", "X", monday, h(30), null, null),          // within 48h
        app(4, "Applied", "X", monday, null, TODAY, null),          // follow-up today
        app(5, "Applied", "X", monday, h(24 * 20), null, null));    // nothing to do
    List<Action> acts = Metrics.actions(new Input(act, List.of(), List.of(), Map.of(), List.of(), NOW, 50));
    check("closed application never in needs-action", acts.stream().noneMatch(x -> x.applicationId() == 2));
    check("quiet application not in needs-action", acts.stream().noneMatch(x -> x.applicationId() == 5));
    check("three actionable items found", acts.stream().map(Action::applicationId).distinct().count() == 3);
    check("sorted by priority score descending", java.util.stream.IntStream.range(1, acts.size()).allMatch(i -> acts.get(i).priorityScore() <= acts.get(i - 1).priorityScore()));
    IvRow past = new IvRow(1, 1, h(-30), "Technical", "Pending", null, h(-100));
    List<Action> a2 = Metrics.actions(new Input(List.of(app(1, "Interview", "X", monday, null, null, null)), List.of(), List.of(past), Map.of(), List.of(), NOW, 50));
    check("past interview without outcome is flagged", a2.stream().anyMatch(x -> x.actionLabel().equals("Record outcome")));
    List<Action> aMissing = Metrics.actions(new Input(List.of(app(9, "Interview", "X", monday, null, null, null)), List.of(), List.of(), Map.of(), List.of(), NOW, 50));
    check("Interview status without a round asks to add interview", aMissing.stream().anyMatch(x -> x.actionLabel().equals("Add interview")));
    check("upcoming interviews metric counts only future records",
        Metrics.summary(new Input(List.of(app(1, "Interview", "X", monday, null, null, null)), List.of(), List.of(new IvRow(1, 1, h(40), "HR", "Pending", null, h(-1))), Map.of(), List.of(), NOW, 8)).interviewsScheduled() == 1
        && Metrics.summary(new Input(List.of(app(1, "Interview", "X", monday, null, null, null)), List.of(), List.of(), Map.of(), List.of(), NOW, 8)).interviewsScheduled() == 0);

    IvRow soon = new IvRow(2, 1, h(20), "HR", "Pending", null, h(-1));
    List<Action> a3 = Metrics.actions(new Input(List.of(app(1, "Interview", "X", monday, null, null, null)), List.of(), List.of(soon), Map.of(1L, 2), List.of(), NOW, 50));
    check("incomplete prep before an interview is flagged", a3.stream().anyMatch(x -> x.actionLabel().equals("Finish prep")));
    check("action list honors the limit", Metrics.summary(new Input(act, List.of(), List.of(), Map.of(), List.of(), NOW, 1)).actions().size() == 1);
    check("week starts Monday", Metrics.weekStart(LocalDate.of(2026, 3, 15)).equals(LocalDate.of(2026, 3, 9)) && Metrics.weekStart(monday).equals(monday));
  }


  static void notifications() {
    ZoneId z = ZoneOffset.UTC;
    NotificationPlanner.Prefs prefs = new NotificationPlanner.Prefs(48, 24, true, z);
    NotificationPlanner.PlanApp open = new NotificationPlanner.PlanApp(1, "Acme", "SWE", true, h(20), null, "Applied");
    NotificationPlanner.PlanApp closed = new NotificationPlanner.PlanApp(2, "ClosedCo", "Role", false, h(10), null, "Selected");
    List<NotificationPlanner.Planned> out = NotificationPlanner.plan(List.of(open, closed), List.of(), prefs, NOW);
    check("deadline soon creates notification", out.stream().anyMatch(p -> p.category().equals("DEADLINE")));
    check("closed app never notified", out.stream().noneMatch(p -> p.appId() != null && p.appId() == 2));
    NotificationPlanner.PlanInterview iv = new NotificationPlanner.PlanInterview(5, 1, h(12), "Assessment", "Pending", null);
    List<NotificationPlanner.Planned> withIv = NotificationPlanner.plan(List.of(open), List.of(iv), prefs, NOW);
    check("assessment round creates ASSESSMENT notification", withIv.stream().anyMatch(p -> p.category().equals("ASSESSMENT")));
    java.util.Set<String> keys = new java.util.HashSet<>();
    boolean unique = true;
    for (NotificationPlanner.Planned p : withIv) unique &= keys.add(p.dedupKey());
    check("notification dedup keys unique", unique);
    List<NotificationPlanner.Planned> again = NotificationPlanner.plan(List.of(open), List.of(iv), prefs, NOW);
    check("same plan yields same keys", withIv.stream().map(NotificationPlanner.Planned::dedupKey).toList()
        .equals(again.stream().map(NotificationPlanner.Planned::dedupKey).toList()));
    NotificationPlanner.PlanApp interviewStatus = new NotificationPlanner.PlanApp(3, "Co", "Role", true, null, null, "Interview");
    List<NotificationPlanner.Planned> noDate = NotificationPlanner.plan(List.of(interviewStatus), List.of(), prefs, NOW);
    check("Interview status without round notifies", noDate.stream().anyMatch(p -> p.category().equals("SYSTEM")));
  }

  static void isolationOfNewAccount() {
    // Two different users' rows are never mixed by the metric functions: they only see what the caller passes in.
    Input a = new Input(List.of(app(1, "Applied", "X", LocalDate.of(2026, 3, 9), null, null, null)), List.of(), List.of(), Map.of(), List.of(), NOW, 8);
    Input b = new Input(List.of(), List.of(), List.of(), Map.of(), List.of(), NOW, 8);
    check("metrics are a pure function of the caller's rows", Metrics.summary(a).total() == 1 && Metrics.summary(b).total() == 0);
  }
}
