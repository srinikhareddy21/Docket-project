package com.docket.notifications;

import java.time.Instant;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
public class NotificationJob {
  private static final Logger log = LoggerFactory.getLogger(NotificationJob.class);
  private final NotificationService service;
  public NotificationJob(NotificationService service) { this.service = service; }

  @Scheduled(fixedDelayString = "PT10M", initialDelayString = "PT2M")
  public void sync() {
    int users = 0;
    for (Long userId : service.allUserIdsWithApps()) {
      try { service.sync(userId, Instant.now()); users++; }
      catch (RuntimeException e) { log.error("Notification sync failed for one user ({})", e.getClass().getSimpleName()); }
    }
    if (users > 0) log.info("Synced in-app notifications for {} users", users);
  }

  @Scheduled(cron = "0 40 3 * * *")
  public void purge() {
    int n = service.purgeOld();
    if (n > 0) log.info("Purged {} old notifications", n);
  }
}
