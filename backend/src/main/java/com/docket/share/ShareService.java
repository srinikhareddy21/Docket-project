package com.docket.share;

import com.docket.config.AppProperties;
import com.docket.dashboard.DashboardService;
import com.docket.dashboard.Metrics;
import com.docket.web.ApiException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ShareService {
  static final int MAX_ACTIVE = 10;
  static final int MAX_DAYS = 90;
  private static final SecureRandom RANDOM = new SecureRandom();

  /** What a public link may show. Counts only: no names, notes, documents, contacts, companies or dates of individual applications. */
  public record PublicSummary(int total, int interviews, int offers, Map<String, Integer> statusCounts, Instant expiresAt) {}
  public record LinkDto(long id, Instant createdAt, Instant expiresAt, boolean revoked, String status, String url) {}

  private final ShareLinkRepository links;
  private final DashboardService dashboard;
  private final AppProperties props;

  public ShareService(ShareLinkRepository links, DashboardService dashboard, AppProperties props) {
    this.links = links; this.dashboard = dashboard; this.props = props;
  }

  @Transactional(readOnly = true)
  public List<LinkDto> list(long userId) {
    Instant now = Instant.now();
    return links.findByUserIdOrderByCreatedAtDesc(userId).stream().map(l -> toDto(l, now, null)).toList();
  }

  /** The only time the full link is ever returned. */
  @Transactional
  public LinkDto create(long userId, int expiresInDays) {
    if (expiresInDays < 1 || expiresInDays > MAX_DAYS)
      throw ApiException.badRequest("Choose an expiry between 1 and " + MAX_DAYS + " days.", Map.of("expiresInDays", "Choose between 1 and " + MAX_DAYS));
    Instant now = Instant.now();
    if (links.countActive(userId, now) >= MAX_ACTIVE)
      throw ApiException.conflict("You have " + MAX_ACTIVE + " active links. Revoke one before creating another.", null);
    byte[] raw = new byte[32];
    RANDOM.nextBytes(raw);
    String token = Base64.getUrlEncoder().withoutPadding().encodeToString(raw);
    ShareLink l = links.save(new ShareLink(userId, sha256(token), now.plus(Duration.ofDays(expiresInDays))));
    String url = props.frontendUrl().replaceAll("/+$", "") + "/share/" + token;
    return toDto(l, now, url);
  }

  @Transactional
  public void revoke(long userId, long id) {
    ShareLink l = links.findByIdAndUserId(id, userId).orElseThrow(() -> ApiException.notFound("We could not find that link."));
    if (l.revokedAt == null) { l.revokedAt = Instant.now(); links.save(l); }
  }

  /** Public. Unknown, malformed, expired and revoked tokens all give the same 404 so nothing can be learned by guessing. */
  @Transactional(readOnly = true)
  public PublicSummary publicView(String token) {
    ApiException gone = ApiException.notFound("This link is not available.");
    if (token == null || token.length() < 20 || token.length() > 64) throw gone;
    ShareLink l = links.findByTokenHash(sha256(token)).orElseThrow(() -> gone);
    if (!l.usableAt(Instant.now())) throw gone;
    Metrics.Summary s = dashboard.summary(l.userId);
    Map<String, Integer> counts = new LinkedHashMap<>();
    for (Metrics.Count c : s.statuses()) counts.put(c.label(), c.count());
    return new PublicSummary(s.total(), s.reachedInterview(), s.offers(), counts, l.expiresAt);
  }

  private static LinkDto toDto(ShareLink l, Instant now, String url) {
    String status = l.revokedAt != null ? "Revoked" : l.expiresAt.isAfter(now) ? "Active" : "Expired";
    return new LinkDto(l.id, l.createdAt, l.expiresAt, l.revokedAt != null, status, url);
  }

  static String sha256(String s) {
    try { return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(s.getBytes(StandardCharsets.UTF_8))); }
    catch (Exception e) { throw new IllegalStateException(e); }
  }

  @Transactional
  public int purgeOld() { return links.purgeBefore(Instant.now().minus(Duration.ofDays(30))); }
}
