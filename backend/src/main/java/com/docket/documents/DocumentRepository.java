package com.docket.documents;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface DocumentRepository extends JpaRepository<DocumentRecord, Long> {
  List<DocumentRecord> findByApplicationIdAndUserIdOrderByUploadedAtDescIdDesc(Long applicationId, Long userId);
  Optional<DocumentRecord> findByIdAndUserId(Long id, Long userId);
  long countByApplicationIdAndUserId(Long applicationId, Long userId);

  boolean existsByStorageKey(String storageKey);

  /** Documents of applications that the retention purge is about to remove for good. */
  @Query("select d from DocumentRecord d where d.applicationId in (select a.id from Application a where a.deletedAt is not null and a.deletedAt < :cutoff)")
  List<DocumentRecord> findForPurge(@Param("cutoff") Instant cutoff);
}
