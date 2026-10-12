package com.docket.auth;

import com.docket.auth.AuthDtos.*;
import com.docket.config.AppProperties;
import com.docket.mail.MailService;
import com.docket.security.JwtService;
import com.docket.security.SlidingWindowLimiter;
import com.docket.user.User;
import com.docket.user.UserRepository;
import com.docket.web.ApiException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.HexFormat;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * Registration, sessions and password reset. ApiException must not roll back: security-relevant writes
 * (e.g. revoking a session after refresh-token reuse) have to persist even when we answer 401.
 */
@Service
@Transactional(noRollbackFor = ApiException.class)
public class AuthService {
  private static final SecureRandom RNG = new SecureRandom();
  private static final Duration ROTATION_GRACE = Duration.ofSeconds(10); // tolerates two tabs refreshing at once
  private static final String SESSION_ENDED = "Your session has ended. Log in again.";

  public record Session(String accessToken, String refreshToken, Duration refreshMaxAge) {}
  public record LoginResult(User user, Session session) {}

  private final UserRepository users;
  private final RefreshTokenRepository refreshTokens;
  private final PasswordResetTokenRepository resetTokens;
  private final PasswordEncoder encoder;
  private final JwtService jwt;
  private final MailService mail;
  private final AppProperties props;
  private final SlidingWindowLimiter loginFailures;
  private final String dummyHash; // compared against when the email is unknown, to keep timing similar

  public AuthService(UserRepository users, RefreshTokenRepository refreshTokens, PasswordResetTokenRepository resetTokens,
                     PasswordEncoder encoder, JwtService jwt, MailService mail, AppProperties props,
                     @Qualifier("loginFailureLimiter") SlidingWindowLimiter loginFailures) {
    this.users = users; this.refreshTokens = refreshTokens; this.resetTokens = resetTokens; this.encoder = encoder;
    this.jwt = jwt; this.mail = mail; this.props = props; this.loginFailures = loginFailures;
    this.dummyHash = encoder.encode("docket-timing-equaliser-Password1!");
  }

  /** Not transactional: a duplicate-key race must surface as a clean 409, not a rollback-only error. */
  @Transactional(propagation = Propagation.NOT_SUPPORTED)
  public User register(RegisterRequest r) {
    String email = normalizeEmail(r.email());
    String violation = PasswordPolicy.violation(r.password());
    if (violation != null) throw ApiException.badRequest("Choose a stronger password.", Map.of("password", violation));
    if (users.existsByEmail(email)) throw duplicate();
    try {
      return users.saveAndFlush(new User(r.name().trim().replaceAll("\\s+", " "), email, encoder.encode(r.password()), Instant.now()));
    } catch (DataIntegrityViolationException e) {
      throw duplicate();
    }
  }

  public LoginResult login(LoginRequest r) {
    String email = normalizeEmail(r.email());
    if (loginFailures.isBlocked(email)) throw ApiException.tooMany("Too many failed attempts. Try again in a few minutes.");
    User user = users.findByEmail(email).orElse(null);
    boolean ok = encoder.matches(r.password(), user != null ? user.getPasswordHash() : dummyHash);
    if (user == null || !ok) {
      loginFailures.record(email);
      throw ApiException.unauthorized("Invalid email or password.");
    }
    loginFailures.clear(email);
    return new LoginResult(user, issue(user.getId(), UUID.randomUUID().toString(), Boolean.TRUE.equals(r.remember())));
  }

  /** Rotates the refresh token. A token reused after its grace window means theft: the whole session is revoked. */
  public Session refresh(String rawToken) {
    if (rawToken == null || rawToken.isBlank()) throw ApiException.unauthorized(SESSION_ENDED);
    RefreshToken t = refreshTokens.findByTokenHash(sha256(rawToken)).orElseThrow(() -> ApiException.unauthorized(SESSION_ENDED));
    Instant now = Instant.now();
    if (t.getRevokedAt() != null || t.getExpiresAt().isBefore(now)) throw ApiException.unauthorized(SESSION_ENDED);
    if (t.getRotatedAt() != null && t.getRotatedAt().isBefore(now.minus(ROTATION_GRACE))) {
      refreshTokens.revokeFamily(t.getFamilyId(), now);
      throw ApiException.unauthorized(SESSION_ENDED);
    }
    if (t.getRotatedAt() == null) t.setRotatedAt(now);
    return issue(t.getUserId(), t.getFamilyId(), t.isRemember());
  }

  public void logout(String rawRefreshToken, String sid) {
    Instant now = Instant.now();
    if (rawRefreshToken != null && !rawRefreshToken.isBlank()) {
      refreshTokens.findByTokenHash(sha256(rawRefreshToken)).ifPresent(t -> refreshTokens.revokeFamily(t.getFamilyId(), now));
    }
    if (sid != null) refreshTokens.revokeFamily(sid, now);
  }

  public User currentUser(long userId) {
    return users.findById(userId).orElseThrow(() -> ApiException.unauthorized(SESSION_ENDED));
  }

  /** Always behaves the same whether or not the email exists. */
  public void forgotPassword(String rawEmail) {
    users.findByEmail(normalizeEmail(rawEmail)).ifPresent(u -> {
      resetTokens.deleteByUserId(u.getId());
      String raw = newToken();
      Duration ttl = props.auth().resetTokenTtl();
      resetTokens.save(new PasswordResetToken(u.getId(), sha256(raw), Instant.now().plus(ttl)));
      mail.sendPasswordReset(u.getEmail(), u.getName(), raw, ttl.toMinutes());
    });
  }

  public void resetPassword(ResetPasswordRequest r) {
    String violation = PasswordPolicy.violation(r.password());
    if (violation != null) throw ApiException.badRequest("Choose a stronger password.", Map.of("password", violation));
    Instant now = Instant.now();
    PasswordResetToken t = resetTokens.findByTokenHash(sha256(r.token()))
        .filter(x -> x.getUsedAt() == null && x.getExpiresAt().isAfter(now))
        .orElseThrow(() -> ApiException.badRequest("This reset link is invalid or has expired. Request a new one.", null));
    User user = users.findById(t.getUserId()).orElseThrow(() -> ApiException.badRequest("This reset link is invalid or has expired. Request a new one.", null));
    user.setPasswordHash(encoder.encode(r.password()));
    t.setUsedAt(now);
    resetTokens.deleteByUserId(user.getId()); // also removes any other outstanding links for this account
    refreshTokens.revokeAllForUser(user.getId(), now); // signs the account out everywhere
    users.save(user);
  }

  private Session issue(long userId, String family, boolean remember) {
    Duration ttl = remember ? props.auth().refreshTtlRemember() : props.auth().refreshTtlSession();
    String raw = newToken();
    refreshTokens.save(new RefreshToken(userId, family, sha256(raw), Instant.now().plus(ttl), remember));
    return new Session(jwt.issue(userId, family), raw, remember ? ttl : null);
  }

  private static ApiException duplicate() {
    return ApiException.conflict("An account with this email already exists.", Map.of("email", "An account with this email already exists."));
  }

  private static String normalizeEmail(String e) { return e.trim().toLowerCase(Locale.ROOT); }

  private static String newToken() {
    byte[] b = new byte[32];
    RNG.nextBytes(b);
    return Base64.getUrlEncoder().withoutPadding().encodeToString(b);
  }

  static String sha256(String s) {
    try {
      return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(s.getBytes(StandardCharsets.UTF_8)));
    } catch (NoSuchAlgorithmException e) {
      throw new IllegalStateException(e);
    }
  }
}
