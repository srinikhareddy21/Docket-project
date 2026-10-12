package com.docket.extension;

import jakarta.persistence.*;
import java.time.Instant;

/** A personal access token for the Chrome extension. Only its SHA-256 is stored; the token is shown once. */
@Entity
@Table(name = "extension_tokens")
public class ExtensionToken {
  @Id @GeneratedValue(strategy = GenerationType.IDENTITY) @Column(name = "token_id") Long id;
  @Column(name = "user_id", nullable = false, updatable = false) Long userId;
  @Column(name = "label", nullable = false, length = 60) String label;
  @Column(name = "token_hash", nullable = false, updatable = false, length = 64) String tokenHash;
  @Column(name = "hint", nullable = false, updatable = false, length = 8) String hint;
  @Column(name = "created_at", nullable = false, updatable = false) Instant createdAt;
  @Column(name = "expires_at", nullable = false, updatable = false) Instant expiresAt;
  @Column(name = "last_used_at") Instant lastUsedAt;
  @Column(name = "revoked_at") Instant revokedAt;

  protected ExtensionToken() {}
  ExtensionToken(Long userId, String label, String tokenHash, String hint, Instant expiresAt) {
    this.userId = userId; this.label = label; this.tokenHash = tokenHash; this.hint = hint; this.expiresAt = expiresAt;
  }
  @PrePersist void onCreate() { createdAt = Instant.now(); }
  boolean usableAt(Instant now) { return revokedAt == null && expiresAt.isAfter(now); }
}
