package com.docket.share;

import jakarta.persistence.*;
import java.time.Instant;

/** A read-only progress link. Only the SHA-256 of the token is stored, so the link itself cannot be recovered later. */
@Entity
@Table(name = "share_links")
public class ShareLink {
  @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
  @Column(name = "share_id") Long id;
  @Column(name = "user_id", nullable = false, updatable = false) Long userId;
  @Column(name = "token_hash", nullable = false, updatable = false, length = 64) String tokenHash;
  @Column(name = "created_at", nullable = false, updatable = false) Instant createdAt;
  @Column(name = "expires_at", nullable = false, updatable = false) Instant expiresAt;
  @Column(name = "revoked_at") Instant revokedAt;

  protected ShareLink() {}
  ShareLink(Long userId, String tokenHash, Instant expiresAt) { this.userId = userId; this.tokenHash = tokenHash; this.expiresAt = expiresAt; }
  @PrePersist void onCreate() { createdAt = Instant.now(); }

  boolean usableAt(Instant now) { return revokedAt == null && expiresAt.isAfter(now); }
}
