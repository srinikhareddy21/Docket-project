package com.docket.autofill;

import com.sun.net.httpserver.HttpServer;
import java.io.ByteArrayOutputStream;
import java.io.OutputStream;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.zip.GZIPOutputStream;

/** Plain-Java checks (no JUnit, no Spring). Run with verify/run-checks.sh. */
public class AutofillChecks {
  static int pass = 0, fail = 0;
  static void check(String name, boolean ok) { if (ok) pass++; else { fail++; System.out.println("FAIL: " + name); } }

  public static void main(String[] a) throws Exception {
    guard(); parser(); fetcher();
    System.out.println("autofill checks: " + pass + " passed, " + fail + " failed");
    if (fail > 0) System.exit(1);
  }

  static boolean blocked(UrlGuard g, String url) {
    try { g.check(url); return false; } catch (UrlGuard.Rejected r) { return true; }
  }

  static void guard() throws Exception {
    UrlGuard g = new UrlGuard();
    String[] bad = {
        "http://127.0.0.1/", "http://localhost/", "http://LOCALHOST./", "http://foo.localhost/", "http://169.254.169.254/latest/meta-data/",
        "http://[::1]/", "http://10.0.0.5/", "http://192.168.1.1/", "http://172.16.0.1/", "http://172.31.255.255/", "http://100.64.0.1/",
        "http://0.0.0.0/", "http://2130706433/", "http://0x7f000001/", "http://127.1/", "http://[::ffff:127.0.0.1]/",
        "http://[fd00::1]/", "http://[fe80::1]/", "http://[2002:7f00:1::1]/", "http://[64:ff9b::7f00:1]/", "http://[::]/",
        "file:///etc/passwd", "ftp://example.com/", "gopher://example.com/", "javascript:alert(1)", "http://user:pw@example.com/",
        "http://example.com:8080/", "http://example.com:22/", "http://printer.local/", "http://metadata.internal/", "http://224.0.0.1/",
        "http://255.255.255.255/", "http://198.18.0.1/", "http://[2001:db8::1]/", "", "   ", "not a url", "http://", "https:///x",
    };
    for (String u : bad) check("blocks " + u, blocked(g, u));
    check("blocks over-long url", blocked(g, "https://example.com/" + "a".repeat(2100)));
    check("blocks url with space", blocked(g, "https://example.com/a b"));

    InetAddress pub = InetAddress.getByAddress(new byte[] {93, (byte) 184, (byte) 216, 34});
    InetAddress priv = InetAddress.getByAddress(new byte[] {10, 0, 0, 1});
    UrlGuard ok = new UrlGuard(h -> new InetAddress[] {pub}, false);
    check("allows public https", !blocked(ok, "https://jobs.example.com/careers/123?utm=1"));
    check("allows public http", !blocked(ok, "http://jobs.example.com/x"));
    check("allows explicit 443", !blocked(ok, "https://jobs.example.com:443/x"));
    UrlGuard mixed = new UrlGuard(h -> new InetAddress[] {pub, priv}, false);
    check("blocks when ANY resolved address is private", blocked(mixed, "https://jobs.example.com/"));
    UrlGuard nx = new UrlGuard(h -> { throw new UrlGuard.UncheckedUnknownHost(new java.net.UnknownHostException("x")); }, false);
    check("unknown host rejected", blocked(nx, "https://nope.example/"));
    check("public IPv6 allowed", UrlGuard.isPublic(InetAddress.getByName("2606:4700:4700::1111"), false));
    check("public IPv4 allowed", UrlGuard.isPublic(InetAddress.getByName("8.8.8.8"), false));
  }

  static final String PAGE = "<html><head><script type=\"application/ld+json\">{\"@context\":\"https://schema.org\",\"@type\":\"JobPosting\","
      + "\"title\":\"Software Engineer Intern\",\"hiringOrganization\":{\"@type\":\"Organization\",\"name\":\"Acme &amp; Co\"},"
      + "\"employmentType\":[\"INTERN\"],\"jobLocationType\":\"TELECOMMUTE\","
      + "\"jobLocation\":{\"@type\":\"Place\",\"address\":{\"addressLocality\":\"Pune\",\"addressRegion\":\"MH\",\"addressCountry\":\"IN\"}},"
      + "\"description\":\"&lt;p&gt;Build things.&lt;/p&gt;&lt;ul&gt;&lt;li&gt;Java&lt;/li&gt;&lt;li&gt;SQL&lt;/li&gt;&lt;/ul&gt;\","
      + "\"skills\":\"Java, SQL; Git\\n- React\",\"validThrough\":\"2026-12-01\","
      + "\"baseSalary\":{\"@type\":\"MonetaryAmount\",\"currency\":\"INR\",\"value\":{\"@type\":\"QuantitativeValue\",\"minValue\":30000,\"maxValue\":40000,\"unitText\":\"MONTH\"}}}"
      + "</script></head><body>hi</body></html>";

  static void parser() {
    JobPageParser.Parsed p = JobPageParser.parse(PAGE);
    check("parser found", p.found());
    Map<String, Object> f = p.fields();
    check("title", "Software Engineer Intern".equals(f.get("position")));
    check("company decodes entity", "Acme & Co".equals(f.get("companyName")));
    check("type internship", "Internship".equals(f.get("jobType")));
    check("remote", "Remote".equals(f.get("workMode")));
    check("location", "Pune, MH, IN".equals(f.get("location")));
    check("skills split", List.of("Java", "SQL", "Git", "React").equals(f.get("requiredSkills")));
    check("salary", "INR 30,000\u201340,000 per month".equals(f.get("salaryOrStipend")));
    check("description text", String.valueOf(f.get("jobDescription")).contains("Build things.") && String.valueOf(f.get("jobDescription")).contains("- Java") && !String.valueOf(f.get("jobDescription")).contains("<"));
    check("date-only deadline end of day", "2026-12-01T23:59:59Z".equals(f.get("deadline")));
    check("date-only warns", p.warnings().size() == 1);

    String graph = "<script type='application/ld+json'>{\"@graph\":[{\"@type\":\"WebSite\"},{\"@type\":[\"Thing\",\"JobPosting\"],\"title\":\"Analyst\",\"hiringOrganization\":\"Globex\",\"employmentType\":\"FULL_TIME\",\"validThrough\":\"2026-11-30T17:00:00+05:30\"}]}</script>";
    JobPageParser.Parsed g = JobPageParser.parse(graph);
    check("graph found", g.found() && "Analyst".equals(g.fields().get("position")) && "Globex".equals(g.fields().get("companyName")));
    check("full-time", "Full-time".equals(g.fields().get("jobType")));
    check("offset deadline converted", "2026-11-30T11:30:00Z".equals(g.fields().get("deadline")));
    check("no fabricated work mode", !g.fields().containsKey("workMode"));
    check("no fabricated skills", !g.fields().containsKey("requiredSkills"));
    check("no fabricated location", !g.fields().containsKey("location"));

    check("no json-ld", !JobPageParser.parse("<html><title>Software Engineer at X</title></html>").found());
    check("broken json", !JobPageParser.parse("<script type=\"application/ld+json\">{ not json</script>").found());
    check("other schema type", !JobPageParser.parse("<script type=\"application/ld+json\">{\"@type\":\"Organization\",\"name\":\"A\"}</script>").found());
    check("posting with neither title nor company", !JobPageParser.parse("<script type=\"application/ld+json\">{\"@type\":\"JobPosting\",\"description\":\"x\"}</script>").found());
    check("null input", !JobPageParser.parse(null).found());
    JobPageParser.Parsed bad = JobPageParser.parse("<script type=\"application/ld+json\">{\"@type\":\"JobPosting\",\"title\":\"T\",\"validThrough\":\"soon\"}</script>");
    check("unreadable deadline skipped with warning", bad.found() && !bad.fields().containsKey("deadline") && bad.warnings().size() == 1);
    String deep = "[".repeat(5000) + "]".repeat(5000);
    check("deep json bounded", !JobPageParser.parse("<script type=\"application/ld+json\">" + deep + "</script>").found());
    String longTitle = "x".repeat(400);
    check("title capped", String.valueOf(JobPageParser.parse("<script type=\"application/ld+json\">{\"@type\":\"JobPosting\",\"title\":\"" + longTitle + "\"}</script>").fields().get("position")).length() == 150);
    check("script in description removed", !String.valueOf(JobPageParser.parse("<script type=\"application/ld+json\">{\"@type\":\"JobPosting\",\"title\":\"T\",\"description\":\"<script>alert(1)</script>Hello\"}</script>").fields().get("jobDescription")).contains("alert"));
  }

  static byte[] gzip(String s) throws Exception {
    ByteArrayOutputStream b = new ByteArrayOutputStream();
    try (GZIPOutputStream g = new GZIPOutputStream(b)) { g.write(s.getBytes(StandardCharsets.UTF_8)); }
    return b.toByteArray();
  }

  static void fetcher() throws Exception {
    HttpServer s = HttpServer.create(new InetSocketAddress(InetAddress.getLoopbackAddress(), 0), 0);
    s.createContext("/ok", ex -> { byte[] b = PAGE.getBytes(StandardCharsets.UTF_8); ex.getResponseHeaders().add("Content-Type", "text/html; charset=utf-8"); ex.sendResponseHeaders(200, b.length); try (OutputStream o = ex.getResponseBody()) { o.write(b); } });
    s.createContext("/chunked", ex -> { byte[] b = PAGE.getBytes(StandardCharsets.UTF_8); ex.getResponseHeaders().add("Content-Type", "text/html"); ex.sendResponseHeaders(200, 0); try (OutputStream o = ex.getResponseBody()) { o.write(b); } });
    s.createContext("/gz", ex -> { byte[] b; try { b = gzip(PAGE); } catch (Exception e) { throw new java.io.IOException(e); } ex.getResponseHeaders().add("Content-Type", "text/html"); ex.getResponseHeaders().add("Content-Encoding", "gzip"); ex.sendResponseHeaders(200, b.length); try (OutputStream o = ex.getResponseBody()) { o.write(b); } });
    s.createContext("/redir", ex -> { ex.getResponseHeaders().add("Location", "/ok"); ex.sendResponseHeaders(302, -1); ex.close(); });
    s.createContext("/loop", ex -> { ex.getResponseHeaders().add("Location", "/loop"); ex.sendResponseHeaders(302, -1); ex.close(); });
    s.createContext("/meta", ex -> { ex.getResponseHeaders().add("Location", "http://169.254.169.254/latest/meta-data/"); ex.sendResponseHeaders(302, -1); ex.close(); });
    s.createContext("/internal", ex -> { ex.getResponseHeaders().add("Location", "http://10.0.0.1/admin"); ex.sendResponseHeaders(301, -1); ex.close(); });
    s.createContext("/gone", ex -> { ex.sendResponseHeaders(404, -1); ex.close(); });
    s.createContext("/blocked", ex -> { ex.sendResponseHeaders(403, -1); ex.close(); });
    s.createContext("/pdf", ex -> { ex.getResponseHeaders().add("Content-Type", "application/pdf"); byte[] b = "%PDF".getBytes(); ex.sendResponseHeaders(200, b.length); try (OutputStream o = ex.getResponseBody()) { o.write(b); } });
    s.createContext("/huge", ex -> { ex.getResponseHeaders().add("Content-Type", "text/html"); ex.sendResponseHeaders(200, 0); try (OutputStream o = ex.getResponseBody()) { byte[] chunk = "a".repeat(100_000).getBytes(); for (int i = 0; i < 40; i++) o.write(chunk); } catch (Exception ignored) { } });
    s.start();
    String base = "http://127.0.0.1:" + s.getAddress().getPort();
    SafeFetcher f = new SafeFetcher(new UrlGuard(h -> { try { return InetAddress.getAllByName(h); } catch (Exception e) { throw new UrlGuard.UncheckedUnknownHost(new java.net.UnknownHostException(h)); } }, true));
    try {
      SafeFetcher.Page ok = f.fetch(base + "/ok");
      check("fetch 200", ok.status() == 200 && JobPageParser.parse(ok.html()).found());
      check("fetch chunked", JobPageParser.parse(f.fetch(base + "/chunked").html()).found());
      check("fetch gzip", JobPageParser.parse(f.fetch(base + "/gz").html()).found());
      check("follows redirect", JobPageParser.parse(f.fetch(base + "/redir").html()).found());
      check("404 reported, no body", f.fetch(base + "/gone").status() == 404 && f.fetch(base + "/gone").html().isEmpty());
      check("403 reported", f.fetch(base + "/blocked").status() == 403);
      SafeFetcher.Page huge = f.fetch(base + "/huge");
      check("huge body truncated", huge.truncated() && huge.html().length() <= SafeFetcher.MAX_BODY_BYTES);
      expect(f, base + "/loop", SafeFetcher.Kind.TOO_MANY_REDIRECTS, "redirect loop stops");
      expect(f, base + "/meta", SafeFetcher.Kind.BLOCKED, "redirect to cloud metadata blocked");
      expect(f, base + "/internal", SafeFetcher.Kind.BLOCKED, "redirect to private IP blocked");
      expect(f, base + "/pdf", SafeFetcher.Kind.UNSUPPORTED_CONTENT, "non-html rejected");
      expect(f, "http://127.0.0.1:1/x", SafeFetcher.Kind.UNREACHABLE, "closed port unreachable");
      SafeFetcher strict = new SafeFetcher(new UrlGuard());
      expect(strict, base + "/ok", SafeFetcher.Kind.BLOCKED, "production guard refuses loopback server");
    } finally { s.stop(0); }
  }

  static void expect(SafeFetcher f, String url, SafeFetcher.Kind kind, String name) {
    try { f.fetch(url); check(name, false); }
    catch (SafeFetcher.FetchException e) { check(name + " (got " + e.kind() + ")", e.kind() == kind); }
  }
}
