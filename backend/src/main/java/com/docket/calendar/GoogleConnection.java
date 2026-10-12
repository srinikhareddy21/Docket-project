package com.docket.calendar;

import jakarta.persistence.*;
import java.time.Instant;

/** One Google Calendar connection per user. The refresh token is stored encrypted, never in plain text. */
@Entity
@Table(name = "google_calendar_connections")
public class GoogleConnection {
  @Id @Column(name = "user_id") Long userId;
  @Column(name = "refresh_token_enc", nullable = false, length = 1024) String refreshTokenEnc;
  @Column(name = "scope", nullable = false, length = 500) String scope;
  @Column(name = "status", nullable = false, length = 16) String status;
  @Column(name = "sync_interviews", nullable = false) boolean syncInterviews = true;
  @Column(name = "sync_follow_ups", nullable = false) boolean syncFollowUps = true;
  @Column(name = "sync_deadlines", nullable = false) boolean syncDeadlines = true;
  @Column(name = "connected_at", nullable = false) Instant connectedAt;
  @Column(name = "last_sync_at") Instant lastSyncAt;
  @Column(name = "last_sync_status", length = 10) String lastSyncStatus;
  @Column(name = "last_sync_note", length = 300) String lastSyncNote;

  protected GoogleConnection() {}
  GoogleConnection(Long userId) { this.userId = userId; }
}
