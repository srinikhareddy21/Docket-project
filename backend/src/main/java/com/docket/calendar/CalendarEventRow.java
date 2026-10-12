package com.docket.calendar;

import jakarta.persistence.*;
import java.time.Instant;

/** Maps one DOCKET item (source_key, e.g. INTERVIEW:12) to the Google event created for it. */
@Entity
@Table(name = "calendar_events")
public class CalendarEventRow {
  @Id @GeneratedValue(strategy = GenerationType.IDENTITY) @Column(name = "row_id") Long id;
  @Column(name = "user_id", nullable = false, updatable = false) Long userId;
  @Column(name = "source_key", nullable = false, updatable = false, length = 40) String sourceKey;
  @Column(name = "google_event_id", nullable = false, length = 64) String googleEventId;
  @Column(name = "content_hash", nullable = false, length = 64) String contentHash;
  @Column(name = "synced_at", nullable = false) Instant syncedAt;

  protected CalendarEventRow() {}
  CalendarEventRow(Long userId, String sourceKey) { this.userId = userId; this.sourceKey = sourceKey; }
}
