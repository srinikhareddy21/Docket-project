package com.docket.documents;

import jakarta.persistence.*;
import java.time.Instant;

/** Metadata row. The bytes live on disk under storage_key (a random name), never under the user's file name. */
@Entity
@Table(name = "documents")
public class DocumentRecord {
  @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
  @Column(name = "document_id") Long id;
  @Column(name = "application_id", nullable = false, updatable = false) Long applicationId;
  @Column(name = "user_id", nullable = false, updatable = false) Long userId;
  @Column(name = "name", nullable = false, length = 150) String name;
  @Column(name = "doc_type", nullable = false, length = 20) String docType;
  @Column(name = "content_type", nullable = false, length = 100) String contentType;
  @Column(name = "size_bytes", nullable = false) long sizeBytes;
  @Column(name = "storage_key", nullable = false, updatable = false, length = 64) String storageKey;
  @Column(name = "uploaded_at", nullable = false, updatable = false) Instant uploadedAt;

  protected DocumentRecord() {}
  DocumentRecord(Long applicationId, Long userId) { this.applicationId = applicationId; this.userId = userId; }
  @PrePersist void onCreate() { uploadedAt = Instant.now(); }
}
