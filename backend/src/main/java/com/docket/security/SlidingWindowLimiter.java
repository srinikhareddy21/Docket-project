package com.docket.security;

import java.time.Duration;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * In-memory sliding-window counter. Suitable for a single backend instance; with several instances
 * move this to a shared store (e.g. Redis). State is lost on restart.
 */
public class SlidingWindowLimiter {
  private final int max;
  private final long windowMs;
  private final Map<String, Deque<Long>> hits = new ConcurrentHashMap<>();

  public SlidingWindowLimiter(int max, Duration window) { this.max = max; this.windowMs = window.toMillis(); }

  /** Records a hit; returns false when the key has exceeded the limit. */
  public boolean tryAcquire(String key) {
    long now = System.currentTimeMillis();
    boolean[] ok = {true};
    hits.compute(key, (k, d) -> {
      Deque<Long> q = d == null ? new ArrayDeque<>() : d;
      prune(q, now);
      if (q.size() >= max) ok[0] = false; else q.addLast(now);
      return q;
    });
    return ok[0];
  }

  public void record(String key) {
    long now = System.currentTimeMillis();
    hits.compute(key, (k, d) -> { Deque<Long> q = d == null ? new ArrayDeque<>() : d; prune(q, now); q.addLast(now); return q; });
  }

  public boolean isBlocked(String key) {
    long now = System.currentTimeMillis();
    boolean[] blocked = {false};
    hits.computeIfPresent(key, (k, q) -> { prune(q, now); blocked[0] = q.size() >= max; return q.isEmpty() ? null : q; });
    return blocked[0];
  }

  public void clear(String key) { hits.remove(key); }

  public void purge() {
    long now = System.currentTimeMillis();
    for (String k : hits.keySet()) {
      hits.computeIfPresent(k, (key, q) -> { prune(q, now); return q.isEmpty() ? null : q; });
    }
  }

  private void prune(Deque<Long> q, long now) { while (!q.isEmpty() && now - q.peekFirst() > windowMs) q.pollFirst(); }

  public long windowSeconds() { return windowMs / 1000; }
}
