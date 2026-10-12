package com.docket.auth;

import com.docket.security.SlidingWindowLimiter;
import java.time.Duration;
import java.time.Instant;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/** Hourly housekeeping: removes long-expired tokens and stale in-memory rate-limit entries. */
@Component
public class TokenCleanupJob {
  private static final Logger log = LoggerFactory.getLogger(TokenCleanupJob.class);
  private final RefreshTokenRepository refresh;
  private final PasswordResetTokenRepository reset;
  private final SlidingWindowLimiter ipLimiter;
  private final SlidingWindowLimiter loginFailureLimiter;
  private final SlidingWindowLimiter shareLimiter;
  private final com.docket.profile.ProfileService profile;

  public TokenCleanupJob(RefreshTokenRepository refresh, PasswordResetTokenRepository reset,
                         @Qualifier("ipLimiter") SlidingWindowLimiter ipLimiter,
                         @Qualifier("loginFailureLimiter") SlidingWindowLimiter loginFailureLimiter,
                         @Qualifier("shareLimiter") SlidingWindowLimiter shareLimiter,
                         com.docket.profile.ProfileService profile) {
    this.profile = profile;
    this.refresh = refresh; this.reset = reset; this.ipLimiter = ipLimiter; this.loginFailureLimiter = loginFailureLimiter; this.shareLimiter = shareLimiter;
  }

  @Scheduled(cron = "0 15 * * * *")
  @Transactional
  public void run() {
    Instant cutoff = Instant.now().minus(Duration.ofDays(1));
    long r = refresh.deleteByExpiresAtBefore(cutoff);
    long p = reset.deleteByExpiresAtBefore(cutoff);
    ipLimiter.purge();
    loginFailureLimiter.purge();
    shareLimiter.purge();
    profile.purgeExpiredEmailTokens();
    if (r + p > 0) log.info("Removed {} expired refresh tokens and {} expired reset tokens", r, p);
  }
}
