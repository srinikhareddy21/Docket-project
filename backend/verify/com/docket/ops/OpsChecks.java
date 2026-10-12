package com.docket.ops;

import com.docket.config.AppProperties;
import java.time.Duration;
import java.util.Base64;
import java.util.List;

/** Checks the production start-up guard's rules (Phase 8). Uses annotation stubs, no Spring needed. */
public class OpsChecks {
  static int pass = 0, fail = 0;
  static void check(String name, boolean ok) { if (ok) pass++; else { fail++; System.out.println("FAIL: " + name); } }

  static String goodSecret() { byte[] b = new byte[48]; new java.security.SecureRandom().nextBytes(b); return Base64.getEncoder().encodeToString(b); }

  static AppProperties props(String secret, boolean secure, String front, List<String> cors, List<String> ext) {
    return new AppProperties(front, cors, "no-reply@example.com", new AppProperties.Jwt(secret, "docket"), new AppProperties.Cookie(secure),
        new AppProperties.Auth(Duration.ofMinutes(15), Duration.ofDays(30), Duration.ofHours(12), Duration.ofMinutes(30)),
        new AppProperties.RateLimit(30, Duration.ofMinutes(10), 5, Duration.ofMinutes(15)),
        new AppProperties.Applications(Duration.ofMinutes(5), Duration.ofDays(30)), new AppProperties.Storage("/data/uploads"),
        new AppProperties.Google("", "", "", ""), new AppProperties.Extension(ext));
  }

  public static void main(String[] a) {
    String good = goodSecret(), site = "https://docket.example.com";
    check("safe configuration has no problems", ProductionGuard.problems(props(good, true, site, List.of(site), List.of())).isEmpty());
    check("extension origin (chrome-extension://) is allowed", ProductionGuard.problems(props(good, true, site, List.of(site), List.of("chrome-extension://abcdefghijklmnopabcdefghijklmnop"))).isEmpty());
    check("short secret refused", !ProductionGuard.problems(props("c2hvcnQ=", true, site, List.of(site), List.of())).isEmpty());
    check("31-byte secret refused", !ProductionGuard.problems(props(Base64.getEncoder().encodeToString(new byte[31]), true, site, List.of(site), List.of())).isEmpty());
    check("placeholder secret refused", !ProductionGuard.problems(props(Base64.getEncoder().encodeToString("change-me-change-me-change-me-change-me".getBytes()), true, site, List.of(site), List.of())).isEmpty());
    check("insecure cookies refused", ProductionGuard.problems(props(good, false, site, List.of(site), List.of())).stream().anyMatch(s -> s.contains("COOKIE_SECURE")));
    check("http frontend refused", ProductionGuard.problems(props(good, true, "http://docket.example.com", List.of(site), List.of())).stream().anyMatch(s -> s.contains("FRONTEND_URL")));
    check("localhost http refused", !ProductionGuard.problems(props(good, true, "http://localhost:5173", List.of("http://localhost:5173"), List.of())).isEmpty());
    check("wildcard CORS refused", ProductionGuard.problems(props(good, true, site, List.of("*"), List.of())).stream().anyMatch(s -> s.contains("wildcard")));
    check("http CORS origin refused", ProductionGuard.problems(props(good, true, site, List.of("http://evil.example"), List.of())).stream().anyMatch(s -> s.contains("https")));
    check("wildcard extension origin refused", !ProductionGuard.problems(props(good, true, site, List.of(site), List.of("chrome-extension://*"))).isEmpty());
    check("messages never echo the secret", ProductionGuard.problems(props("c2hvcnQ=", true, site, List.of(site), List.of())).stream().noneMatch(s -> s.contains("c2hvcnQ")));
    check("several problems are all reported", ProductionGuard.problems(props("x", false, "http://a", List.of("*"), List.of())).size() >= 4);
    for (String h : List.of("localhost", "127.0.0.1", "mailhog", "", "  "))
      check("dev mail host refused: '" + h + "'", ProductionGuard.problems(props(good, true, site, List.of(site), List.of()), h).stream().anyMatch(s -> s.contains("MAIL_HOST")));
    check("null mail host refused", ProductionGuard.problems(props(good, true, site, List.of(site), List.of()), null).stream().anyMatch(s -> s.contains("MAIL_HOST")));
    check("real SMTP host accepted", ProductionGuard.problems(props(good, true, site, List.of(site), List.of()), "smtp.mailprovider.com").isEmpty());
    check("mail check does not hide other problems", ProductionGuard.problems(props(good, false, site, List.of(site), List.of()), "localhost").size() == 2);
    System.out.println("ops checks: " + pass + " passed, " + fail + " failed");
    if (fail > 0) System.exit(1);
  }
}
