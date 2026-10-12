package com.docket.auth;

import jakarta.persistence.*;
import java.time.Instant;

@Entity
@Table(name = "refresh_tokens")
public class RefreshToken {
  @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
  @Column(name = "token_id") private Long id;
  @Column(name = "user_id", nullable = false) private Long userId;
  @Column(name = "family_id", nullable = false, length = 36) private String familyId;
  @Column(name = "token_hash", nullable = false, unique = true, length = 64) private String tokenHash;
  @Column(nullable = false) private boolean remember;
  @Column(name = "expires_at", nullable = false) private Instant expiresAt;
  @Column(name = "rotated_at") private Instant rotatedAt;
  @Column(name = "revoked_at") private Instant revokedAt;
  @Column(name = "created_at", nullable = false, updatable = false) private Instant createdAt;

  protected RefreshToken() {}
  public RefreshToken(Long userId, String familyId, String tokenHash, Instant expiresAt, boolean remember) {
    this.userId = userId; this.familyId = familyId; this.tokenHash = tokenHash; this.expiresAt = expiresAt; this.remember = remember;
  }
  @PrePersist void onCreate() { createdAt = Instant.now(); }

  public Long getUserId() { return userId; }
  public String getFamilyId() { return familyId; }
  public boolean isRemember() { return remember; }
  public Instant getExpiresAt() { return expiresAt; }
  public Instant getRotatedAt() { return rotatedAt; }
  public void setRotatedAt(Instant t) { this.rotatedAt = t; }
  public Instant getRevokedAt() { return revokedAt; }
}
