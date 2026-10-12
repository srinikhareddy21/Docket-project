package com.docket.applications;

import com.docket.documents.DocumentFileCleaner;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Daily housekeeping. Permanently removes applications deleted longer ago than the retention period and the
 * uploaded files that belonged to them. Order matters: files are listed first (the rows are about to vanish),
 * the rows are purged in one transaction, and files are deleted only after that transaction succeeded.
 * Weekly: removes orphaned upload files no row refers to.
 */
@Component
public class ApplicationPurgeJob {
  private static final Logger log = LoggerFactory.getLogger(ApplicationPurgeJob.class);
  private static final Duration ORPHAN_GRACE = Duration.ofDays(1);   // never touch a file an upload may still be writing

  private final ApplicationService service;
  private final DocumentFileCleaner files;

  public ApplicationPurgeJob(ApplicationService service, DocumentFileCleaner files) { this.service = service; this.files = files; }

  @Scheduled(cron = "0 30 3 * * *")
  public void run() {
    Instant cutoff = service.purgeCutoff();
    List<DocumentFileCleaner.StoredFile> stored = files.filesOfApplicationsDeletedBefore(cutoff);
    int n = service.purgeDeletedBefore(cutoff);
    int removed = files.delete(stored);
    if (n > 0) log.info("Permanently removed {} applications past the retention period and {} stored files", n, removed);
  }

  @Scheduled(cron = "0 45 3 * * SUN")
  public void sweepOrphans() {
    int n = files.sweepOrphans(ORPHAN_GRACE);
    if (n > 0) log.info("Removed {} orphaned upload files", n);
  }
}
