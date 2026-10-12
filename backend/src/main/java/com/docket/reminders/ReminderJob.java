package com.docket.reminders;

import java.time.Instant;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Every 5 minutes: for each user who turned email reminders on, bring reminders up to date and send what is due.
 * Runs on one instance only (no distributed lock yet): with two instances, a reminder could be emailed twice.
 */
@Component
public class ReminderJob {
  private static final Logger log = LoggerFactory.getLogger(ReminderJob.class);
  private final ReminderService service;
  public ReminderJob(ReminderService service) { this.service = service; }

  @Scheduled(fixedDelayString = "PT5M", initialDelayString = "PT1M")
  public void deliver() {
    int total = 0;
    for (Long userId : service.usersToProcess()) {
      try { total += service.deliverDue(userId, Instant.now()); }
      catch (RuntimeException e) { log.error("Reminder run failed for one user ({})", e.getClass().getSimpleName()); }
    }
    if (total > 0) log.info("Sent {} reminder emails", total);
  }

  @Scheduled(cron = "0 30 3 * * *")
  public void purge() {
    int n = service.purgeOld();
    if (n > 0) log.info("Removed {} old reminders", n);
  }
}
