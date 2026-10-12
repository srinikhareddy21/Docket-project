package com.docket.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.Set;
import org.springframework.web.filter.OncePerRequestFilter;

/** Per-IP throttle on the credential endpoints. Behind a proxy, set FORWARD_HEADERS_STRATEGY=native. */
public class AuthRateLimitFilter extends OncePerRequestFilter {
  private static final Set<String> LIMITED = Set.of(
      "/api/auth/login", "/api/auth/register", "/api/auth/forgot-password", "/api/auth/reset-password", "/api/auth/confirm-email-change");
  private final SlidingWindowLimiter limiter;
  private final SlidingWindowLimiter shareLimiter;

  public AuthRateLimitFilter(SlidingWindowLimiter limiter, SlidingWindowLimiter shareLimiter) { this.limiter = limiter; this.shareLimiter = shareLimiter; }

  @Override
  protected void doFilterInternal(HttpServletRequest req, HttpServletResponse res, FilterChain chain)
      throws ServletException, IOException {
    if ("POST".equals(req.getMethod()) && LIMITED.contains(req.getRequestURI()) && !limiter.tryAcquire(req.getRemoteAddr())) {
      res.setHeader("Retry-After", Long.toString(limiter.windowSeconds()));
      JsonErrors.write(res, 429, "Too many attempts. Wait a few minutes and try again.");
      return;
    }
    // Public share links are unauthenticated, so throttle guessing per IP with their own budget.
    if ("GET".equals(req.getMethod()) && req.getRequestURI().startsWith("/api/share/") && !shareLimiter.tryAcquire(req.getRemoteAddr())) {
      res.setHeader("Retry-After", Long.toString(shareLimiter.windowSeconds()));
      JsonErrors.write(res, 429, "Too many requests. Wait a few minutes and try again.");
      return;
    }
    chain.doFilter(req, res);
  }
}
