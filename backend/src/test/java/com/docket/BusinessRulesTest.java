package com.docket;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.docket.applications.Csv;
import com.docket.applications.Normalizer;
import com.docket.applications.Priority;
import com.docket.auth.PasswordPolicy;
import com.docket.documents.FileSniffer;
import com.docket.skills.SkillMatcher;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * Pure business rules, no Spring context and no database, so they run in `mvn verify` in about a second.
 * (The same rules are also cross-checked against the frontend by scripts/parity.test.mjs.)
 */
class BusinessRulesTest {
  static final Instant NOW = Instant.parse("2026-10-10T12:00:00Z");
  static final LocalDate TODAY = LocalDate.of(2026, 10, 10);

  static Priority.Result prio(String status, Instant deadline, LocalDate followUp, Instant changed, Instant interview, boolean prepIncomplete) {
    return Priority.compute(status, deadline, followUp, changed, interview, prepIncomplete, NOW, TODAY);
  }
  static Instant in(Duration d) { return NOW.plus(d); }

  // ---------------- priority ----------------
  @Test void closedApplicationsAlwaysScoreZero() {
    for (String s : List.of("Selected", "Rejected", "Withdrawn")) assertEquals(0, prio(s, in(Duration.ofHours(1)), TODAY, null, null, true).score());
  }
  @Test void deadlineTiers() {
    assertEquals(50, prio("Applied", in(Duration.ofHours(-1)), null, null, null, false).score());
    assertEquals(55, prio("Applied", in(Duration.ofHours(47)), null, null, null, false).score());
    assertEquals(30, prio("Applied", in(Duration.ofDays(6)), null, null, null, false).score());
    assertEquals(15, prio("Applied", in(Duration.ofDays(13)), null, null, null, false).score());
    assertEquals(0, prio("Applied", in(Duration.ofDays(30)), null, null, null, false).score());
  }
  @Test void followUpIsJudgedInWholeDays() {
    assertEquals(25, prio("Applied", null, TODAY, null, null, false).score());
    assertEquals(35, prio("Applied", null, TODAY.minusDays(1), null, null, false).score());
    assertEquals(0, prio("Applied", null, TODAY.plusDays(1), null, null, false).score());
  }
  @Test void anOldApplicationAloneNeverDominates() {
    Priority.Result r = prio("Applied", null, null, in(Duration.ofDays(-400)), null, false);
    assertEquals(5, r.score());
    assertEquals("Low", r.level());
  }
  @Test void interviewTomorrowIsHigh() {
    Priority.Result r = prio("Interview", null, null, null, in(Duration.ofHours(24)), false);
    assertEquals(50, r.score());
    assertEquals("High", r.level());
  }
  @Test void deadlineTomorrowIsHigh() {
    Priority.Result r = prio("Applied", in(Duration.ofHours(24)), null, null, null, false);
    assertEquals(55, r.score());
    assertEquals("High", r.level());
  }
  @Test void imminentInterviewWithIncompletePrepIsHigh() {
    // deadline within 48h (55) + interview within 48h (50) + prep (15) = 120 → capped 100
    Priority.Result r = prio("Interview", in(Duration.ofHours(10)), null, null, in(Duration.ofHours(20)), true);
    assertEquals(100, r.score());
    assertEquals("High", r.level());
    assertEquals(3, r.reasons().size());
  }
  @Test void prepGapOnlyCountsWithinSevenDays() {
    assertEquals(30 + 15, prio("Applied", null, null, null, in(Duration.ofDays(3)), true).score());
    assertEquals(10, prio("Applied", null, null, null, in(Duration.ofDays(20)), true).score());
  }
  @Test void closedIgnoresUrgentDates() {
    assertEquals(0, prio("Selected", in(Duration.ofHours(1)), TODAY, null, in(Duration.ofHours(2)), true).score());
    assertEquals(0, prio("Rejected", in(Duration.ofHours(-1)), null, null, null, false).score());
  }

  // ---------------- skill match ----------------
  @Test void eightOfElevenIsAbout72Point7() {
    List<String> have = List.of("a", "b", "c", "d", "e", "f", "g", "h");
    List<String> need = List.of("a", "b", "c", "d", "e", "f", "g", "h", "x", "y", "z");
    SkillMatcher.Result r = SkillMatcher.match(have, need);
    assertEquals(11, r.total());
    assertEquals(8, r.matched().size());
    assertEquals(3, r.missing().size());
    assertEquals(72.7, Math.round(r.score() * 10) / 10.0);
  }
  @Test void noRequiredSkillsMeansNoScoreNotZero() {
    assertNull(SkillMatcher.match(List.of("Java"), List.of()).score());
    assertNull(SkillMatcher.match(List.of("Java"), List.of(" ", "")).score());
  }
  @Test void caseSpacingAndAliasesAreNormalizedAndDuplicatesCountOnce() {
    SkillMatcher.Result r = SkillMatcher.match(List.of("JavaScript"), List.of("js", "JavaScript", " JS "));
    assertEquals(1, r.total());
    assertEquals(100.0, r.score().doubleValue());
    assertEquals(0.0, SkillMatcher.match(List.of(), List.of("Java")).score().doubleValue());
  }

  // ---------------- duplicate detection keys ----------------
  @Test void companyAndRoleIgnoreCaseSpacingAndPunctuation() {
    assertEquals(Normalizer.text("Acme, Inc."), Normalizer.text("  ACME   inc "));
  }
  @Test void urlKeyIgnoresSchemeWwwTrackingAndFragment() {
    String a = Normalizer.urlKey("https://www.jobs.test/careers/42?utm_source=x#apply");
    String b = Normalizer.urlKey("http://jobs.test/careers/42/");
    assertFalse(a.isEmpty());
    assertEquals(a, b);
    assertEquals("", Normalizer.urlKey(null));
    assertEquals("", Normalizer.urlKey("   "));
  }
  @Test void likeWildcardsInSearchAreEscaped() {
    assertEquals("%100!%!_off!!%", Normalizer.likePattern("100%_off!"));
    assertEquals("", Normalizer.likePattern("  "));
  }

  // ---------------- CSV ----------------
  @Test void csvQuotesAndEscapes() {
    assertEquals("Acme", Csv.cell("Acme"));
    assertEquals("\"Acme, Inc\"", Csv.cell("Acme, Inc"));
    assertEquals("\"say \"\"hi\"\"\"", Csv.cell("say \"hi\""));
    assertEquals("", Csv.cell(null));
    assertEquals("a,\"b,c\"\r\n", Csv.row(List.of("a", "b,c")));
  }
  @Test void csvNeutralisesSpreadsheetFormulas() {
    for (String bad : List.of("=HYPERLINK(\"x\")", "+1+1", "-2+3", "@SUM(A1)")) assertTrue(Csv.cell(bad).startsWith("'") || Csv.cell(bad).startsWith("\"'"), bad);
  }

  // ---------------- passwords ----------------
  @Test void passwordRules() {
    assertNull(PasswordPolicy.violation("Docket#Test1234"));
    for (String weak : new String[]{null, "", "short1A!", "alllowercase1!", "ALLUPPERCASE1!", "NoDigitsHere!!", "NoSymbols12345"}) assertNotNull(PasswordPolicy.violation(weak), String.valueOf(weak));
    assertNotNull(PasswordPolicy.violation("Aa1!" + "x".repeat(80)), "over 72 bytes is refused, not silently truncated");
  }

  // ---------------- uploads: type comes from the bytes, never the name ----------------
  static FileSniffer.Kind sniff(byte[] bytes) throws Exception {
    Path f = Files.createTempFile("docket-sniff", ".bin");
    try { Files.write(f, bytes); return FileSniffer.detect(f); } finally { Files.deleteIfExists(f); }
  }
  @Test void realFilesAreRecognisedByContent() throws Exception {
    assertEquals(FileSniffer.Kind.PDF, sniff("%PDF-1.4\n%%EOF".getBytes()));
    assertEquals(FileSniffer.Kind.PNG, sniff(new byte[]{(byte) 0x89, 'P', 'N', 'G', 0x0D, 0x0A, 0x1A, 0x0A, 0}));
    assertEquals(FileSniffer.Kind.JPEG, sniff(new byte[]{(byte) 0xFF, (byte) 0xD8, (byte) 0xFF, (byte) 0xE0}));
  }
  @Test void disguisedOrUnknownFilesAreRejected() throws Exception {
    assertNull(sniff("MZ\u0090 this is an executable".getBytes()));
    assertNull(sniff("<script>alert(1)</script>".getBytes()));
    assertNull(sniff(new byte[0]));
    assertNull(sniff(new byte[]{'P', 'K', 3, 4, 0, 0}), "a zip that is not a .docx is refused");
  }
  @Test void displayNamesCannotCarryPathsOrControlCharacters() {
    assertEquals("passwd", FileSniffer.displayName("../../etc/passwd"));
    assertEquals("cv.pdf", FileSniffer.displayName("C:\\x\\cv.pdf"));
    assertEquals("cvfdp.exe", FileSniffer.displayName("cv\u202Efdp.exe"));
    assertEquals("document", FileSniffer.displayName("   "));
    assertTrue(FileSniffer.displayName("x".repeat(400) + ".pdf").length() <= 150);
  }

  // ---- Phase 8 regression tests ----

  @org.junit.jupiter.api.Test void listFilterIsEmptyStringNeverNull() {
    // Bug: blank filters were bound as NULL, so `:status = ''` was false and the list/search/board showed nothing.
    assertEquals("", com.docket.applications.ApplicationService.filter(null));
    assertEquals("", com.docket.applications.ApplicationService.filter("   "));
    assertEquals("Applied", com.docket.applications.ApplicationService.filter(" Applied "));
  }

  @org.junit.jupiter.api.Test void searchPatternIsCaseInsensitive() {
    assertEquals("%salesforce%", com.docket.applications.Normalizer.likePattern("SalesForce"));
    assertEquals("", com.docket.applications.Normalizer.likePattern(" "));
  }

  @org.junit.jupiter.api.Test void csvKeepsPopulatedAndBlankOptionalFields() {
    String row = com.docket.applications.Csv.row(java.util.Arrays.asList("Acme, Inc", "Intern", "Applied", "2026-10-10", "2026-12-01T10:00:00Z", "LinkedIn", "50.0"));
    assertEquals("\"Acme, Inc\",Intern,Applied,2026-10-10,2026-12-01T10:00:00Z,LinkedIn,50.0\r\n", row);
    String blank = com.docket.applications.Csv.row(java.util.Arrays.asList("Acme", "Intern", "Applied", null, null, null, null));
    assertEquals("Acme,Intern,Applied,,,,\r\n", blank);
    assertEquals("\"say \"\"hi\"\"\nnow\"", com.docket.applications.Csv.cell("say \"hi\"\nnow"));
  }

  @org.junit.jupiter.api.Test void autofillMetaFallbackReadsJobPageMetadata() {
    String html = "<html><head><meta property=\"og:title\" content=\"Software Engineer Intern | Acme\"><meta property=\"og:site_name\" content=\"Acme\">"
        + "<meta property=\"og:url\" content=\"https://jobs.acme.test/roles/123\"><meta name=\"description\" content=\"Build things &amp; ship.\"></head></html>";
    var p = com.docket.autofill.JobPageParser.parseMeta(html, java.net.URI.create("https://jobs.acme.test/roles/123/"));
    assertTrue(p.found());
    assertEquals("Software Engineer Intern", p.fields().get("position"));
    assertEquals("Acme", p.fields().get("companyName"));
    assertEquals("Build things & ship.", p.fields().get("jobDescription"));
    assertFalse(p.fields().containsKey("location"));   // never invented
  }

  @org.junit.jupiter.api.Test void autofillMetaFallbackIgnoresGenericListingPages() {
    // e.g. a closed posting that redirects to the careers home: its title is not about the job.
    String html = "<title>Salesforce Jobs | Salesforce</title><meta property=\"og:url\" content=\"https://www.salesforce.com/company/careers/jobs/\">";
    var p = com.docket.autofill.JobPageParser.parseMeta(html, java.net.URI.create("https://www.salesforce.com/company/careers/jobs/jr337715/summer-2027-intern-software-engineer/"));
    assertFalse(p.found());
    // A listing page that declares itself still is not a job.
    String list = "<meta property=\"og:url\" content=\"https://jobs.lever.co/palantir\"><meta property=\"og:title\" content=\"Palantir Technologies jobs\">";
    assertFalse(com.docket.autofill.JobPageParser.parseMeta(list, java.net.URI.create("https://jobs.lever.co/palantir")).found());
    // No declared URL: not trusted.
    assertFalse(com.docket.autofill.JobPageParser.parseMeta("<title>GitLab</title>", java.net.URI.create("https://x.test/a")).found());
  }
}
