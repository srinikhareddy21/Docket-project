package com.docket.extension;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/** Daily: deletes extension tokens that expired or were revoked more than 30 days ago. */
@Component
public class ExtensionCleanupJob {
  private final ExtensionTokenService service;
  public ExtensionCleanupJob(ExtensionTokenService service) { this.service = service; }
  @Scheduled(cron = "0 55 3 * * *") public void run() { service.purgeOld(); }
}
