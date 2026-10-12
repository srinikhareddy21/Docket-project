package com.docket.checklist;

import java.util.List;
import java.util.Optional;
import java.util.Set;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ChecklistRepository extends JpaRepository<ChecklistItem, Long> {
  List<ChecklistItem> findByApplicationIdAndUserIdOrderByIdAsc(Long applicationId, Long userId);
  Optional<ChecklistItem> findByIdAndUserId(Long id, Long userId);
  long countByApplicationIdAndUserId(Long applicationId, Long userId);

  /** Applications that still have unfinished interview preparation. Feeds the priority score. */
  @Query("select distinct c.applicationId from ChecklistItem c where c.userId = :userId and c.type = 'PREP' and c.completed = false")
  Set<Long> applicationsWithIncompletePrep(@Param("userId") Long userId);

  /** Rows are [applicationId, number of unfinished preparation items], live applications only. */
  @Query("select c.applicationId, count(c) from ChecklistItem c where c.userId = :userId and c.type = 'PREP' and c.completed = false "
      + "and c.applicationId in (select a.id from Application a where a.userId = :userId and a.deletedAt is null) group by c.applicationId")
  List<Object[]> incompletePrepCounts(@Param("userId") Long userId);

  /** Most recently completed items (by last update), live applications only. */
  @Query("select c from ChecklistItem c where c.userId = :userId and c.completed = true "
      + "and c.applicationId in (select a.id from Application a where a.userId = :userId and a.deletedAt is null) order by c.updatedAt desc")
  List<ChecklistItem> recentlyCompleted(@Param("userId") Long userId, Pageable pageable);
}
