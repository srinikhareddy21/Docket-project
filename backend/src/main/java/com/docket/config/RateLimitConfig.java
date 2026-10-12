package com.docket.config;

import com.docket.security.SlidingWindowLimiter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class RateLimitConfig {
  @Bean SlidingWindowLimiter ipLimiter(AppProperties p) {
    return new SlidingWindowLimiter(p.rateLimit().ipMax(), p.rateLimit().ipWindow());
  }
  @Bean SlidingWindowLimiter loginFailureLimiter(AppProperties p) {
    return new SlidingWindowLimiter(p.rateLimit().loginFailureMax(), p.rateLimit().loginFailureWindow());
  }
  /** Public share-link views: 60 per IP per 10 minutes. */
  @Bean SlidingWindowLimiter shareLimiter() { return new SlidingWindowLimiter(60, java.time.Duration.ofMinutes(10)); }
  /** Job-link autofill makes the server fetch a third-party page: 15 per user per 10 minutes. */
  @Bean SlidingWindowLimiter autofillLimiter() { return new SlidingWindowLimiter(15, java.time.Duration.ofMinutes(10)); }
  /** Email-change requests send mail to a third-party address: 5 per user per hour. */
  @Bean SlidingWindowLimiter emailChangeLimiter() { return new SlidingWindowLimiter(5, java.time.Duration.ofHours(1)); }
  /** Chrome extension API: 60 requests per user per minute. */
  @Bean SlidingWindowLimiter extensionLimiter() { return new SlidingWindowLimiter(60, java.time.Duration.ofMinutes(1)); }
  /** Manual Google Calendar syncs: 6 per user per 10 minutes. */
  @Bean SlidingWindowLimiter calendarSyncLimiter() { return new SlidingWindowLimiter(6, java.time.Duration.ofMinutes(10)); }
}
