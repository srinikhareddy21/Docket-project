package com.docket.applications;

import java.util.List;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.data.jpa.repository.JpaRepository;

public interface StatusHistoryRepository extends JpaRepository<StatusHistory, Long> {
  List<StatusHistory> findByApplicationIdAndUserIdOrderByChangedAtDescIdDesc(Long applicationId, Long userId);

  /** History of this user's live (not deleted) applications. */
  @Query("select h from StatusHistory h where h.userId = :userId and h.applicationId in (select a.id from Application a where a.userId = :userId and a.deletedAt is null)")
  List<StatusHistory> forLiveApplications(@Param("userId") Long userId);
}
