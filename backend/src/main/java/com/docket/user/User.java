package com.docket.user;

import jakarta.persistence.*;
import java.time.Instant;

@Entity
@Table(name = "users")
public class User {
  @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
  @Column(name = "user_id") private Long id;
  @Column(nullable = false, length = 120) private String name;
  @Column(nullable = false, unique = true, length = 254) private String email;
  @Column(name = "password_hash", nullable = false, length = 100) private String passwordHash;
  @Column(name = "email_verified", nullable = false) private boolean emailVerified;
  @Column(name = "terms_accepted_at", nullable = false) private Instant termsAcceptedAt;
  @Column(name = "created_at", nullable = false, updatable = false) private Instant createdAt;
  @Column(name = "updated_at", nullable = false) private Instant updatedAt;

  protected User() {}
  public User(String name, String email, String passwordHash, Instant termsAcceptedAt) {
    this.name = name; this.email = email; this.passwordHash = passwordHash; this.termsAcceptedAt = termsAcceptedAt;
  }

  @PrePersist void onCreate() { createdAt = updatedAt = Instant.now(); }
  @PreUpdate void onUpdate() { updatedAt = Instant.now(); }

  public Long getId() { return id; }
  public String getName() { return name; }
  public String getEmail() { return email; }
  public String getPasswordHash() { return passwordHash; }
  public void setPasswordHash(String h) { this.passwordHash = h; }
  public void setName(String name) { this.name = name; }
  public void setEmail(String email) { this.email = email; }
  public boolean isEmailVerified() { return emailVerified; }
  public void setEmailVerified(boolean v) { this.emailVerified = v; }
  public Instant getCreatedAt() { return createdAt; }
}
