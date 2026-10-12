package com.docket.share;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/** Daily: deletes links that expired or were revoked more than 30 days ago. */
@Component
public class ShareCleanupJob {
  private final ShareService service;
  public ShareCleanupJob(ShareService service) { this.service = service; }
  @Scheduled(cron = "0 45 3 * * *") public void run() { service.purgeOld(); }
}
