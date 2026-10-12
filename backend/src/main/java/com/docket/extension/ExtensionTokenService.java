package com.docket.extension;

import com.docket.calendar.CalendarPlanner;
import com.docket.web.ApiException;
import java.security.SecureRandom;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.List;
import java.util.Optional;
import org.springframework.stereotype.Service;

@Service
public class ExtensionTokenService {
  static final String PREFIX = "dkx_";
  static final int MAX_ACTIVE = 5;
  static final Duration LIFETIME = Duration.ofDays(90);
  private static final SecureRandom RNG = new SecureRandom();
  private final ExtensionTokenRepository repo;

  public ExtensionTokenService(ExtensionTokenRepository repo) { this.repo = repo; }

  public record TokenDto(long id, String label, String hint, Instant createdAt, Instant expiresAt, Instant lastUsedAt, Instant revokedAt, String state) {}
  /** token is present only in the response to creation. */
  public record CreatedDto(TokenDto info, String token) {}

  public List<TokenDto> list(long userId) { return repo.findByUserIdOrderByCreatedAtDesc(userId).stream().map(t -> dto(t, Instant.now())).toList(); }

  public CreatedDto create(long userId, String label) {
    String clean = label == null ? "" : label.trim();
    if (clean.isEmpty()) clean = "Chrome extension";
    if (clean.length() > 60) throw ApiException.badRequest("Keep the name under 60 characters.", java.util.Map.of("label", "Keep the name under 60 characters."));
    if (repo.countActive(userId, Instant.now()) >= MAX_ACTIVE) throw ApiException.conflict("You already have " + MAX_ACTIVE + " active extension tokens. Revoke one first.", null);
    byte[] raw = new byte[32];
    RNG.nextBytes(raw);
    String token = PREFIX + Base64.getUrlEncoder().withoutPadding().encodeToString(raw);
    ExtensionToken t = repo.save(new ExtensionToken(userId, clean, CalendarPlanner.sha256(token), token.substring(token.length() - 4), Instant.now().plus(LIFETIME)));
    return new CreatedDto(dto(t, Instant.now()), token);
  }

  public void revoke(long userId, long id) {
    ExtensionToken t = repo.findByIdAndUserId(id, userId).orElseThrow(() -> ApiException.notFound("That token was not found."));
    if (t.revokedAt == null) { t.revokedAt = Instant.now(); repo.save(t); }
  }

  /** Resolves a presented token to its owner, or empty. Touches last-used at most once a minute. */
  public Optional<Long> authenticate(String presented) {
    if (presented == null || !presented.startsWith(PREFIX) || presented.length() > 100) return Optional.empty();
    Instant now = Instant.now();
    return repo.findByTokenHash(CalendarPlanner.sha256(presented)).filter(t -> t.usableAt(now)).map(t -> {
      if (t.lastUsedAt == null || t.lastUsedAt.isBefore(now.minusSeconds(60))) repo.touch(t.id, now);
      return t.userId;
    });
  }

  public int purgeOld() { return repo.purgeBefore(Instant.now().minus(Duration.ofDays(30))); }

  private static TokenDto dto(ExtensionToken t, Instant now) {
    String state = t.revokedAt != null ? "REVOKED" : t.expiresAt.isAfter(now) ? "ACTIVE" : "EXPIRED";
    return new TokenDto(t.id, t.label, t.hint, t.createdAt, t.expiresAt, t.lastUsedAt, t.revokedAt, state);
  }
}
