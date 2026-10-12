package com.docket.calendar;

import jakarta.persistence.*;
import java.time.Instant;

/** One-time value tying a Google consent screen to the user who started it. Only its SHA-256 is stored. */
@Entity
@Table(name = "oauth_states")
public class OAuthState {
  @Id @GeneratedValue(strategy = GenerationType.IDENTITY) @Column(name = "state_id") Long id;
  @Column(name = "user_id", nullable = false, updatable = false) Long userId;
  @Column(name = "state_hash", nullable = false, updatable = false, length = 64) String stateHash;
  @Column(name = "expires_at", nullable = false, updatable = false) Instant expiresAt;
  @Column(name = "created_at", nullable = false, updatable = false) Instant createdAt;

  protected OAuthState() {}
  OAuthState(Long userId, String stateHash, Instant expiresAt) { this.userId = userId; this.stateHash = stateHash; this.expiresAt = expiresAt; }
  @PrePersist void onCreate() { createdAt = Instant.now(); }
}
