package com.docket.calendar;

import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.transaction.annotation.Transactional;

public interface CalendarEventRepository extends JpaRepository<CalendarEventRow, Long> {
  List<CalendarEventRow> findByUserId(Long userId);
  Optional<CalendarEventRow> findByUserIdAndSourceKey(Long userId, String sourceKey);
  @Transactional long deleteByUserIdAndSourceKey(Long userId, String sourceKey);
  @Transactional long deleteByUserId(Long userId);
}
