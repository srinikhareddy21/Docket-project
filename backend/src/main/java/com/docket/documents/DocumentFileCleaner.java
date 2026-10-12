package com.docket.documents;

import com.docket.config.AppProperties;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Keeps the upload folder in step with the database.
 *
 *  - When applications are purged after the retention period their document rows disappear (ON DELETE CASCADE);
 *    the stored files are collected BEFORE that happens and removed AFTER it committed.
 *  - A weekly sweep removes files that no row refers to (for example after a crash between writing a file and saving its row).
 *    It only touches {@code <numeric user id>/<32 hex characters>} files older than the grace period, nothing else.
 */
@Component
public class DocumentFileCleaner {
  private static final Logger log = LoggerFactory.getLogger(DocumentFileCleaner.class);
  private static final Pattern USER_DIR = Pattern.compile("\\d{1,19}");
  private static final Pattern KEY = Pattern.compile("[0-9a-f]{32}");

  public record StoredFile(long userId, String storageKey) {}

  private final DocumentRepository repo;
  private final Path root;

  public DocumentFileCleaner(DocumentRepository repo, AppProperties props) {
    this.repo = repo;
    this.root = Path.of(props.storage().dir()).toAbsolutePath().normalize();
  }

  /** Files that belong to applications about to be purged. Call before the purge, delete after it commits. */
  @Transactional(readOnly = true)
  public List<StoredFile> filesOfApplicationsDeletedBefore(Instant cutoff) {
    List<StoredFile> out = new ArrayList<>();
    for (DocumentRecord d : repo.findForPurge(cutoff)) out.add(new StoredFile(d.userId, d.storageKey));
    return out;
  }

  /** Removes the given files from disk. Returns how many were removed; failures are logged without paths and never thrown. */
  public int delete(List<StoredFile> files) {
    int removed = 0;
    for (StoredFile f : files) {
      if (!KEY.matcher(f.storageKey()).matches()) continue;
      Path p = root.resolve(Long.toString(f.userId())).resolve(f.storageKey()).normalize();
      if (!p.startsWith(root)) continue;
      try { if (Files.deleteIfExists(p)) removed++; }
      catch (IOException e) { log.warn("Could not delete one stored file of a purged application"); }
    }
    return removed;
  }

  /** Deletes files that no document row refers to and that are older than {@code grace}. Returns how many were removed. */
  public int sweepOrphans(Duration grace) {
    if (!Files.isDirectory(root)) return 0;
    Instant cutoff = Instant.now().minus(grace);
    int removed = 0;
    try (var users = Files.list(root)) {
      for (Path userDir : (Iterable<Path>) users::iterator) {
        if (!Files.isDirectory(userDir) || !USER_DIR.matcher(userDir.getFileName().toString()).matches()) continue;
        try (var files = Files.list(userDir)) {
          for (Path f : (Iterable<Path>) files::iterator) {
            String name = f.getFileName().toString();
            if (!Files.isRegularFile(f) || !KEY.matcher(name).matches()) continue;
            if (Files.getLastModifiedTime(f).toInstant().isAfter(cutoff)) continue;
            if (repo.existsByStorageKey(name)) continue;
            try { Files.deleteIfExists(f); removed++; }
            catch (IOException e) { log.warn("Could not delete one orphaned file"); }
          }
        }
      }
    } catch (IOException e) {
      log.warn("Orphan file sweep stopped early: {}", e.getClass().getSimpleName());
    }
    return removed;
  }
}
