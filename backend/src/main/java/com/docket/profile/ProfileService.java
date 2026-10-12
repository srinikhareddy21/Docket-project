package com.docket.profile;

import com.docket.auth.PasswordPolicy;
import com.docket.auth.RefreshTokenRepository;
import com.docket.config.AppProperties;
import com.docket.mail.MailService;
import com.docket.security.SlidingWindowLimiter;
import com.docket.user.User;
import com.docket.user.UserRepository;
import com.docket.web.ApiException;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.HexFormat;
import java.util.Locale;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Profile, password change, verified email change and account deletion.
 * Each operation that must survive a failure answer (e.g. revoking sessions) runs in its own explicit transaction,
 * so a 4xx never rolls back a security-relevant write and a deletion removes files only after the database commit.
 */
@Service
public class ProfileService {
  private static final Logger log = LoggerFactory.getLogger(ProfileService.class);
  private static final SecureRandom RNG = new SecureRandom();
  private static final Duration EMAIL_TOKEN_TTL = Duration.ofMinutes(60);

  private final UserRepository users;
  private final RefreshTokenRepository sessions;
  private final EmailChangeTokens emailTokens;
  private final PasswordEncoder encoder;
  private final MailService mail;
  private final AppProperties props;
  private final SlidingWindowLimiter passwordAttempts;
  private final SlidingWindowLimiter emailChangeLimiter;
  private final TransactionTemplate tx;

  public ProfileService(UserRepository users, RefreshTokenRepository sessions, EmailChangeTokens emailTokens, PasswordEncoder encoder,
                        MailService mail, AppProperties props, PlatformTransactionManager txm,
                        @Qualifier("loginFailureLimiter") SlidingWindowLimiter passwordAttempts,
                        @Qualifier("emailChangeLimiter") SlidingWindowLimiter emailChangeLimiter) {
    this.users = users; this.sessions = sessions; this.emailTokens = emailTokens; this.encoder = encoder; this.mail = mail; this.props = props;
    this.passwordAttempts = passwordAttempts; this.emailChangeLimiter = emailChangeLimiter; this.tx = new TransactionTemplate(txm);
  }

  public User get(long userId) { return users.findById(userId).orElseThrow(() -> ApiException.unauthorized("Your session has ended. Log in again.")); }

  public User updateName(long userId, String name) {
    return tx.execute(s -> {
      User u = get(userId);
      u.setName(name.trim().replaceAll("\\s+", " "));
      return users.save(u);
    });
  }

  /** Verifies the current password (throttled per account), sets the new one, signs out all OTHER devices. */
  public void changePassword(long userId, String currentSessionId, String current, String next) {
    String key = "pw:" + userId;
    if (passwordAttempts.isBlocked(key)) throw ApiException.tooMany("Too many wrong passwords. Try again in a few minutes.");
    String violation = PasswordPolicy.violation(next);
    User u = get(userId);
    if (!encoder.matches(current, u.getPasswordHash())) {
      passwordAttempts.record(key);
      throw ApiException.badRequest("Current password is incorrect.", Map.of("currentPassword", "Current password is incorrect."));
    }
    if (violation != null) throw ApiException.badRequest("Choose a stronger password.", Map.of("newPassword", violation));
    if (encoder.matches(next, u.getPasswordHash())) throw ApiException.badRequest("Choose a password you have not used for this account.", Map.of("newPassword", "Choose a different password"));
    passwordAttempts.clear(key);
    String hash = encoder.encode(next);
    tx.executeWithoutResult(s -> {
      User fresh = get(userId);
      fresh.setPasswordHash(hash);
      users.save(fresh);
      sessions.revokeOthers(userId, currentSessionId, Instant.now());
    });
  }

  /**
   * Always answers the same way, so this cannot be used to discover which addresses have accounts.
   * The link goes to the NEW address; nothing changes until it is opened.
   */
  public void requestEmailChange(long userId, String rawEmail) {
    String email = rawEmail.trim().toLowerCase(Locale.ROOT);
    User u = get(userId);
    if (email.equals(u.getEmail())) throw ApiException.badRequest("That is already your email address.", Map.of("email", "That is already your email address"));
    if (!emailChangeLimiter.tryAcquire("ec:" + userId)) throw ApiException.tooMany("Too many email change requests. Try again in an hour.");
    if (users.existsByEmail(email)) return; // silent: do not reveal that the address has an account
    String raw = newToken();
    emailTokens.replace(userId, email, sha256(raw), Instant.now().plus(EMAIL_TOKEN_TTL));
    mail.sendEmailChange(email, u.getName(), raw, EMAIL_TOKEN_TTL.toMinutes());
  }

  public void confirmEmailChange(String rawToken) {
    String bad = "This link is invalid or has expired. Request the change again from your profile.";
    EmailChangeTokens.Row t = emailTokens.findByHash(sha256(rawToken)).filter(r -> r.usedAt() == null && r.expiresAt().isAfter(Instant.now()))
        .orElseThrow(() -> ApiException.badRequest(bad, null));
    String[] oldAddress = new String[1];
    String[] name = new String[1];
    tx.executeWithoutResult(s -> {
      User u = users.findById(t.userId()).orElseThrow(() -> ApiException.badRequest(bad, null));
      if (users.existsByEmail(t.newEmail())) throw ApiException.conflict("That email address is no longer available.", null);
      oldAddress[0] = u.getEmail(); name[0] = u.getName();
      u.setEmail(t.newEmail());
      u.setEmailVerified(true);
      users.save(u);
      emailTokens.deleteForUser(u.getId());
      sessions.revokeAllForUser(u.getId(), Instant.now()); // the login identity changed: sign in again
    });
    mail.sendEmailChanged(oldAddress[0], name[0], t.newEmail());
  }

  /** Permanently deletes the account after a password check. Database rows cascade; uploaded files are removed after the commit. */
  public void deleteAccount(long userId, String password) {
    String key = "pw:" + userId;
    if (passwordAttempts.isBlocked(key)) throw ApiException.tooMany("Too many wrong passwords. Try again in a few minutes.");
    User u = get(userId);
    if (!encoder.matches(password, u.getPasswordHash())) {
      passwordAttempts.record(key);
      throw ApiException.badRequest("Password is incorrect.", Map.of("password", "Password is incorrect"));
    }
    passwordAttempts.clear(key);
    tx.executeWithoutResult(s -> users.deleteById(userId));
    removeUploads(userId);
  }

  private void removeUploads(long userId) {
    Path root = Path.of(props.storage().dir()).toAbsolutePath().normalize();
    Path dir = root.resolve(Long.toString(userId)).normalize();
    if (!dir.startsWith(root) || dir.equals(root) || !Files.isDirectory(dir)) return;
    try (var walk = Files.walk(dir)) {
      walk.sorted(java.util.Comparator.reverseOrder()).forEach(p -> { try { Files.deleteIfExists(p); } catch (IOException e) { log.warn("Could not remove one stored file after account deletion"); } });
    } catch (IOException e) {
      log.warn("Could not clean up stored files after account deletion");
    }
  }

  public int purgeExpiredEmailTokens() { return emailTokens.deleteExpired(Instant.now().minus(Duration.ofDays(1))); }

  private static String newToken() { byte[] b = new byte[32]; RNG.nextBytes(b); return Base64.getUrlEncoder().withoutPadding().encodeToString(b); }

  static String sha256(String s) {
    try { return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(s.getBytes(StandardCharsets.UTF_8))); }
    catch (java.security.NoSuchAlgorithmException e) { throw new IllegalStateException(e); }
  }
}
