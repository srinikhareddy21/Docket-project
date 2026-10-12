package com.docket.auth;

import jakarta.persistence.*;
import java.time.Instant;

@Entity
@Table(name = "password_reset_tokens")
public class PasswordResetToken {
  @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
  @Column(name = "token_id") private Long id;
  @Column(name = "user_id", nullable = false) private Long userId;
  @Column(name = "token_hash", nullable = false, unique = true, length = 64) private String tokenHash;
  @Column(name = "expires_at", nullable = false) private Instant expiresAt;
  @Column(name = "used_at") private Instant usedAt;
  @Column(name = "created_at", nullable = false, updatable = false) private Instant createdAt;

  protected PasswordResetToken() {}
  public PasswordResetToken(Long userId, String tokenHash, Instant expiresAt) {
    this.userId = userId; this.tokenHash = tokenHash; this.expiresAt = expiresAt;
  }
  @PrePersist void onCreate() { createdAt = Instant.now(); }

  public Long getUserId() { return userId; }
  public Instant getExpiresAt() { return expiresAt; }
  public Instant getUsedAt() { return usedAt; }
  public void setUsedAt(Instant t) { this.usedAt = t; }
}
