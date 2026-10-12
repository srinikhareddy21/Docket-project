package com.docket.calendar;

import com.docket.applications.Application;
import com.docket.applications.ApplicationRepository;
import com.docket.calendar.CalendarPlanner.*;
import com.docket.config.AppProperties;
import com.docket.interviews.Interview;
import com.docket.interviews.InterviewRepository;
import com.docket.security.SlidingWindowLimiter;
import com.docket.web.ApiException;
import java.security.SecureRandom;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

/**
 * Google Calendar connection and sync. Real OAuth2 and real Calendar API calls only: when the integration is not
 * configured, every call says so and nothing is faked. Tokens and event contents are never logged.
 * Deliberately not @Transactional: Google calls are slow and must not hold a database transaction open.
 */
@Service
public class CalendarService {
  private static final Logger log = LoggerFactory.getLogger(CalendarService.class);
  private static final SecureRandom RNG = new SecureRandom();
  private static final Duration STATE_TTL = Duration.ofMinutes(10);

  private final AppProperties props;
  private final GoogleConnectionRepository conns;
  private final CalendarEventRepository events;
  private final OAuthStateRepository states;
  private final ApplicationRepository apps;
  private final InterviewRepository interviews;
  private final SlidingWindowLimiter syncLimiter;
  private final GoogleClient google;
  private final TokenCrypto crypto;
  private final Set<Long> running = ConcurrentHashMap.newKeySet();

  public CalendarService(AppProperties props, GoogleConnectionRepository conns, CalendarEventRepository events, OAuthStateRepository states,
                         ApplicationRepository apps, InterviewRepository interviews, @Qualifier("calendarSyncLimiter") SlidingWindowLimiter syncLimiter) {
    this.props = props; this.conns = conns; this.events = events; this.states = states; this.apps = apps; this.interviews = interviews; this.syncLimiter = syncLimiter;
    AppProperties.Google g = props.google();
    if (g.configured()) {
      this.google = new GoogleClient(GoogleClient.Config.google(g.clientId(), g.clientSecret(), g.redirectUri()));
      this.crypto = new TokenCrypto(g.tokenEncryptionKey());
    } else { this.google = null; this.crypto = null; }
  }

  public boolean configured() { return google != null; }

  // ---------- status ----------

  public record StatusDto(boolean configured, boolean connected, String status, boolean syncInterviews, boolean syncFollowUps, boolean syncDeadlines,
                          Instant connectedAt, Instant lastSyncAt, String lastSyncStatus, String lastSyncNote, int syncedEvents, String message) {}

  public StatusDto status(long userId) {
    if (!configured()) return new StatusDto(false, false, "NOT_CONFIGURED", true, true, true, null, null, null, null, 0,
        "Google Calendar is not set up on this server. Set GOOGLE_CLIENT_ID, GOOGLE_CLIENT_SECRET, GOOGLE_REDIRECT_URI and TOKEN_ENCRYPTION_KEY (base64 of 32 bytes), then restart. Nothing is synced.");
    GoogleConnection c = conns.findById(userId).orElse(null);
    if (c == null) return new StatusDto(true, false, "NOT_CONNECTED", true, true, true, null, null, null, null, 0, "Not connected. Nothing is synced to Google Calendar.");
    int n = events.findByUserId(userId).size();
    boolean ok = "CONNECTED".equals(c.status);
    return new StatusDto(true, ok, c.status, c.syncInterviews, c.syncFollowUps, c.syncDeadlines, c.connectedAt, c.lastSyncAt, c.lastSyncStatus, c.lastSyncNote, n,
        ok ? null : "Google no longer accepts this connection. Reconnect to resume syncing.");
  }

  // ---------- connect ----------

  public String startConnect(long userId) {
    requireConfigured();
    byte[] raw = new byte[32];
    RNG.nextBytes(raw);
    String state = Base64.getUrlEncoder().withoutPadding().encodeToString(raw);
    states.purge(Instant.now(), userId); // drops expired states and this user's earlier pending one
    states.save(new OAuthState(userId, CalendarPlanner.sha256(state), Instant.now().plus(STATE_TTL)));
    return google.authorizationUrl(state);
  }

  /** Returns a short result code the UI shows: connected, denied, expired or failed. */
  public String finishConnect(String code, String state, String error) {
    if (!configured()) return "failed";
    if (state == null || state.isBlank() || state.length() > 200) return "failed";
    OAuthState st = states.findByStateHash(CalendarPlanner.sha256(state)).orElse(null);
    if (st == null) return "expired";
    states.delete(st); // single use
    if (st.expiresAt.isBefore(Instant.now())) return "expired";
    if (error != null || code == null || code.isBlank()) return "denied";
    try {
      GoogleClient.Tokens t = google.exchangeCode(code);
      if (t.refreshToken() == null || t.refreshToken().isBlank()) return "failed";
      if (t.scope() == null || !t.scope().contains(GoogleClient.SCOPE)) {
        google.revoke(t.refreshToken());
        return "denied"; // calendar permission was not granted, so there is nothing useful to keep
      }
      GoogleConnection c = conns.findById(st.userId).orElseGet(() -> new GoogleConnection(st.userId));
      c.refreshTokenEnc = crypto.encrypt(t.refreshToken());
      c.scope = t.scope().length() > 500 ? t.scope().substring(0, 500) : t.scope();
      c.status = "CONNECTED";
      c.connectedAt = Instant.now();
      c.lastSyncNote = null; c.lastSyncStatus = null;
      conns.save(c);
      return "connected";
    } catch (GoogleClient.AuthException e) {
      log.info("Google code exchange failed");
      return "failed";
    }
  }

  // ---------- preferences ----------

  public record PrefsRequest(boolean syncInterviews, boolean syncFollowUps, boolean syncDeadlines) {}

  public StatusDto savePrefs(long userId, PrefsRequest in) {
    GoogleConnection c = conns.findById(userId).orElseThrow(() -> ApiException.notFound("Google Calendar is not connected."));
    c.syncInterviews = in.syncInterviews(); c.syncFollowUps = in.syncFollowUps(); c.syncDeadlines = in.syncDeadlines();
    conns.save(c);
    return status(userId);
  }

  // ---------- sync ----------

  /** Manual sync from the UI. Rate limited. Returns the real outcome. */
  public StatusDto syncNow(long userId) {
    requireConfigured();
    if (!syncLimiter.tryAcquire("u" + userId)) throw ApiException.tooMany("You synced a few times already. Wait a few minutes and try again.");
    GoogleConnection c = conns.findById(userId).orElseThrow(() -> ApiException.notFound("Google Calendar is not connected."));
    if (!"CONNECTED".equals(c.status)) throw ApiException.conflict("Reconnect Google Calendar first.", null);
    if (!running.add(userId)) throw ApiException.conflict("A sync is already running. Try again in a moment.", null);
    try { runSync(c); } finally { running.remove(userId); }
    return status(userId);
  }

  /**
   * Background sync for one user. When called from a write (e.g. interview create), waits until that
   * transaction commits so the new interview is visible. Skips if already running or not configured.
   */
  public void syncQuietly(long userId) {
    if (!configured()) return;
    Runnable run = () -> {
      if (!running.add(userId)) return;
      try {
        conns.findById(userId).filter(c -> "CONNECTED".equals(c.status)).ifPresent(this::runSync);
      } catch (RuntimeException e) {
        log.error("Background calendar sync failed ({})", e.getClass().getSimpleName());
      } finally { running.remove(userId); }
    };
    if (TransactionSynchronizationManager.isSynchronizationActive()) {
      TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
        @Override public void afterCommit() { run.run(); }
      });
    } else {
      run.run();
    }
  }

  public List<Long> connectedUsers() { return configured() ? conns.connectedUserIds() : List.of(); }

  private void runSync(GoogleConnection c) {
    long userId = c.userId;
    String access;
    try {
      access = google.refreshAccessToken(crypto.decrypt(c.refreshTokenEnc));
    } catch (GoogleClient.AuthException e) {
      if (e.grantRevoked()) { c.status = "NEEDS_RECONNECT"; record(c, "FAILED", "Google access was revoked or expired. Reconnect to resume."); }
      else record(c, "FAILED", e.getMessage());
      return;
    } catch (IllegalArgumentException e) {
      c.status = "NEEDS_RECONNECT";
      record(c, "FAILED", "The stored Google connection could not be read (the server's encryption key may have changed). Reconnect.");
      return;
    }
    List<Application> appRows = apps.findByUserIdAndDeletedAtIsNullOrderByApplicationDateDescIdDesc(userId);
    List<AppRow> a = appRows.stream().map(x -> new AppRow(x.getId(), x.getCompanyName(), x.getPosition(), x.getCurrentStatus(), x.getDeadline(), x.getFollowUpDate())).toList();
    List<InterviewRow> iv = new ArrayList<>();
    for (Object[] r : interviews.findWithApplication(userId, 0L)) {
      Interview i = (Interview) r[0];
      iv.add(new InterviewRow(i.getId(), i.getApplicationId(), i.getInterviewDate(), i.getRound(), i.getInterviewMode(), i.getMeetingLink(), i.getFollowUpDate()));
    }
    Plan plan = CalendarPlanner.plan(a, iv, new Prefs(c.syncInterviews, c.syncFollowUps, c.syncDeadlines), Instant.now(), props.frontendUrl().replaceAll("/+$", ""));
    Map<String, SyncEngine.Mapped> mapped = events.findByUserId(userId).stream()
        .collect(Collectors.toMap(e -> e.sourceKey, e -> new SyncEngine.Mapped(e.googleEventId, e.contentHash), (x, y) -> x));
    SyncEngine.Outcome out = SyncEngine.run(userId, access, plan, mapped, google, new SyncEngine.Store() {
      public void put(String key, String eventId, String hash) {
        CalendarEventRow row = events.findByUserIdAndSourceKey(userId, key).orElseGet(() -> new CalendarEventRow(userId, key));
        row.googleEventId = eventId; row.contentHash = hash; row.syncedAt = Instant.now();
        events.save(row);
      }
      public void remove(String key) { events.deleteByUserIdAndSourceKey(userId, key); }
    });
    record(c, out.status(), out.note());
  }

  private void record(GoogleConnection c, String status, String note) {
    c.lastSyncAt = Instant.now();
    c.lastSyncStatus = status;
    c.lastSyncNote = note == null ? null : (note.length() > 300 ? note.substring(0, 300) : note);
    conns.save(c);
  }

  // ---------- disconnect ----------

  public record DisconnectResult(boolean revokedAtGoogle, int eventsRemoved, int eventsFailed) {}

  public DisconnectResult disconnect(long userId, boolean removeEvents) {
    requireConfigured();
    GoogleConnection c = conns.findById(userId).orElseThrow(() -> ApiException.notFound("Google Calendar is not connected."));
    int removed = 0, failed = 0;
    String refresh = null;
    try { refresh = crypto.decrypt(c.refreshTokenEnc); } catch (IllegalArgumentException ignored) { /* nothing usable to revoke */ }
    if (removeEvents && refresh != null) {
      try {
        String access = google.refreshAccessToken(refresh);
        for (CalendarEventRow e : events.findByUserId(userId)) {
          GoogleClient.Result r = google.delete(access, e.googleEventId);
          if (r.ok() || r.status() == 404 || r.status() == 410) removed++; else failed++;
        }
      } catch (GoogleClient.AuthException e) {
        failed = events.findByUserId(userId).size();
      }
    }
    boolean revoked = refresh != null && google.revoke(refresh);
    events.deleteByUserId(userId);
    conns.delete(c);
    return new DisconnectResult(revoked, removed, failed);
  }

  private void requireConfigured() {
    if (!configured()) throw ApiException.notImplemented("Google Calendar is not set up on this server. Add the Google credentials first.");
  }
}
