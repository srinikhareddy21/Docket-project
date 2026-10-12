package com.docket.reminders;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface ReminderPreferenceRepository extends JpaRepository<ReminderPreference, Long> {
  /** Users who opted in to email. Only these are processed by the background job. */
  @Query("select p.userId from ReminderPreference p where p.emailEnabled = true")
  List<Long> emailEnabledUserIds();
}
