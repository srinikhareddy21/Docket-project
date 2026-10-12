package com.docket.reminders;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ReminderRepository extends JpaRepository<Reminder, Long> {
  Optional<Reminder> findByIdAndUserId(Long id, Long userId);

  List<Reminder> findByUserIdAndDedupKeyIn(Long userId, Collection<String> keys);

  /** Rows that may still be sent or need housekeeping: everything not finished. */
  List<Reminder> findByUserIdAndStatus(Long userId, String status);

  @Query("select r from Reminder r where r.userId = :userId and r.status <> 'DISMISSED' and r.eventAt >= :since order by r.remindAt asc, r.id asc")
  List<Reminder> visible(@Param("userId") Long userId, @Param("since") Instant since, Pageable page);

  long countByUserIdAndType(Long userId, String type);

  @Modifying
  @Query("delete from Reminder r where r.eventAt < :cutoff")
  int purgeEventsBefore(@Param("cutoff") Instant cutoff);
}
