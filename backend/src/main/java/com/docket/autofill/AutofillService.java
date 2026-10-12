package com.docket.autofill;

import com.docket.autofill.AutofillDtos.AutofillResponse;
import com.docket.security.SlidingWindowLimiter;
import com.docket.web.ApiException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;

/**
 * Job-link autofill. Fetches the page server-side through SafeFetcher (SSRF-guarded) and reads only the
 * schema.org JobPosting data. Never logs the URL (it can carry tokens), only the outcome.
 */
@Service
public class AutofillService {
  private static final Logger log = LoggerFactory.getLogger(AutofillService.class);
  static final String META_SOURCE = "Page title and summary metadata (Open Graph / HTML) published by the page";
  static final String SOURCE = "Structured job data (schema.org JobPosting) published by the page";
  private static final List<String> ORDER = List.of("companyName", "position", "jobType", "workMode", "location",
      "jobDescription", "requiredSkills", "deadline", "salaryOrStipend");

  private final SafeFetcher fetcher = new SafeFetcher(new UrlGuard());
  private final SlidingWindowLimiter limiter;

  public AutofillService(@Qualifier("autofillLimiter") SlidingWindowLimiter limiter) { this.limiter = limiter; }

  private static boolean samePath(String url, java.net.URI finalUri) {
    try { return JobPageParser.samePage(url, finalUri); } catch (Exception e) { return false; }
  }

  public AutofillResponse run(long userId, String url) {
    if (!limiter.tryAcquire("u" + userId)) throw ApiException.tooMany("You have used autofill a lot in a short time. Wait a few minutes and try again.");
    SafeFetcher.Page page;
    try {
      page = fetcher.fetch(url);
    } catch (SafeFetcher.FetchException e) {
      log.info("Autofill fetch failed: {}", e.kind());
      return switch (e.kind()) {
        case INVALID, BLOCKED -> throw ApiException.badRequest(e.getMessage(), Map.of("url", e.getMessage()));
        case TIMEOUT -> AutofillResponse.notFound("TIMEOUT", "The website took too long to respond. Try again, or enter the details manually.");
        case TOO_MANY_REDIRECTS -> AutofillResponse.notFound("REDIRECTS", "The link redirects too many times. Open it in your browser and paste the final address.");
        case UNSUPPORTED_CONTENT -> AutofillResponse.notFound("UNSUPPORTED_CONTENT", "This link does not lead to a readable web page. Enter the details manually.");
        case TLS_FAILED -> AutofillResponse.notFound("TLS", "We could not make a secure connection to that website. Enter the details manually.");
        case UNREACHABLE -> AutofillResponse.notFound("UNREACHABLE", "We could not reach that website. Check the link, or enter the details manually.");
      };
    }
    int s = page.status();
    if (s == 401 || s == 403 || s == 451 || s == 999) return AutofillResponse.notFound("BLOCKED_BY_SITE",
        "The website did not let us read this page. Many job boards require a login or block automated access. Enter the details manually.");
    if (s == 404 || s == 410) return AutofillResponse.notFound("NOT_FOUND", "This listing could not be found. It may have expired or been removed.");
    if (s < 200 || s >= 300) return AutofillResponse.notFound("SITE_ERROR", "The website is not responding properly right now (error " + s + "). Try again later or enter the details manually.");

    JobPageParser.Parsed p = JobPageParser.parse(page.html());
    boolean fromMeta = false;
    // Title/summary metadata is only trusted when we landed on the page that was asked for: a redirect to another
    // page (a closed posting sent to the careers home, an "error" listing) describes something else.
    if (!p.found() && page.finalUri() != null && samePath(url, page.finalUri())) { p = JobPageParser.parseMeta(page.html(), page.finalUri()); fromMeta = p.found(); }
    if (!p.found()) {
      log.info("Autofill: page reached, no structured job data");
      return AutofillResponse.notFound("NO_STRUCTURED_DATA",
          "We reached the page, but it does not publish job details in a form we can read. Nothing was filled in. Enter the details manually."
          + (page.finalUri() != null && !samePath(url, page.finalUri()) ? " The link redirected to a different page, so the listing may have closed or moved." : ""));
    }
    List<String> extracted = new ArrayList<>();
    for (String k : ORDER) if (p.fields().containsKey(k)) extracted.add(k);
    List<String> warnings = new ArrayList<>(p.warnings());
    if (page.truncated()) warnings.add("The page was very large, so only the first part was read. Some details may be missing.");
    return new AutofillResponse(true, "OK", "Found " + extracted.size() + " detail" + (extracted.size() == 1 ? "" : "s")
        + " on the page. Check each one before saving.", p.fields(), extracted, warnings, fromMeta ? META_SOURCE : SOURCE);
  }
}
