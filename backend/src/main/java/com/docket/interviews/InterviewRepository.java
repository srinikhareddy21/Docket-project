package com.docket.interviews;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface InterviewRepository extends JpaRepository<Interview, Long> {
  Optional<Interview> findByIdAndUserId(Long id, Long userId);

  /** Rows are [Interview, Application]. Interviews of deleted applications are left out. */
  @Query("""
      select i, a from Interview i, Application a
      where a.id = i.applicationId and i.userId = :userId and a.userId = :userId and a.deletedAt is null
        and (:applicationId = 0L or i.applicationId = :applicationId)
      order by i.interviewDate asc, i.id asc
      """)
  List<Object[]> findWithApplication(@Param("userId") Long userId, @Param("applicationId") long applicationId);

  /** Rows are [applicationId, earliest upcoming interview]. Feeds the priority score. */
  @Query("select i.applicationId, min(i.interviewDate) from Interview i where i.userId = :userId and i.interviewDate >= :now group by i.applicationId")
  List<Object[]> nextInterviews(@Param("userId") Long userId, @Param("now") Instant now);
}
