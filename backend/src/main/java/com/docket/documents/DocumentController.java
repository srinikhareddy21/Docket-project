package com.docket.documents;

import com.docket.applications.ApplicationRepository;
import com.docket.config.AppProperties;
import com.docket.security.AuthUser;
import com.docket.web.ApiException;
import jakarta.validation.constraints.Pattern;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

/**
 * Real file storage. Every read and write is scoped to the signed-in user; files are stored under random names
 * in a per-user folder and served only through this authorized endpoint, always as a download.
 */
@RestController
@RequestMapping("/api")
public class DocumentController {
  private static final Logger log = LoggerFactory.getLogger(DocumentController.class);
  private static final long MAX_BYTES = 5L * 1024 * 1024;
  private static final int MAX_PER_APPLICATION = 20;

  public record DocumentResponse(Long id, Long applicationId, String name, String type, String contentType, long sizeBytes, Instant uploadedAt) {}

  private final DocumentRepository repo;
  private final ApplicationRepository apps;
  private final Path root;

  public DocumentController(DocumentRepository repo, ApplicationRepository apps, AppProperties props) throws IOException {
    this.repo = repo; this.apps = apps;
    this.root = Path.of(props.storage().dir()).toAbsolutePath().normalize();
    Files.createDirectories(root);
  }

  @GetMapping("/applications/{appId}/documents") @Transactional(readOnly = true)
  public List<DocumentResponse> list(@AuthenticationPrincipal AuthUser user, @PathVariable long appId) {
    requireApplication(user, appId);
    return repo.findByApplicationIdAndUserIdOrderByUploadedAtDescIdDesc(appId, user.userId()).stream().map(DocumentController::toResponse).toList();
  }

  @PostMapping(path = "/applications/{appId}/documents", consumes = MediaType.MULTIPART_FORM_DATA_VALUE) @ResponseStatus(HttpStatus.CREATED)
  public DocumentResponse upload(@AuthenticationPrincipal AuthUser user, @PathVariable long appId, @RequestParam("file") MultipartFile file,
      @RequestParam("type") @Pattern(regexp = "Resume|Cover letter|Certificate|Other", message = "Choose a valid document type") String type) throws IOException {
    requireApplication(user, appId);
    if (file.isEmpty()) throw ApiException.badRequest("That file is empty.", null);
    if (file.getSize() > MAX_BYTES) throw tooLarge();
    if (repo.countByApplicationIdAndUserId(appId, user.userId()) >= MAX_PER_APPLICATION)
      throw ApiException.badRequest("This application already has the maximum of " + MAX_PER_APPLICATION + " documents.", null);

    String name = FileSniffer.displayName(file.getOriginalFilename());
    String key = UUID.randomUUID().toString().replace("-", "");
    Path dir = userDir(user.userId());
    Files.createDirectories(dir);
    Path target = dir.resolve(key).normalize();
    if (!target.startsWith(dir)) throw new IllegalStateException("Unsafe storage path");

    try {
      try (InputStream in = file.getInputStream()) { Files.copy(in, target, StandardCopyOption.REPLACE_EXISTING); }
      if (Files.size(target) > MAX_BYTES) throw tooLarge();
      FileSniffer.Kind kind = FileSniffer.detect(target);
      if (kind == null || !kind.allowsExtension(FileSniffer.extension(name)))
        throw ApiException.badRequest("That file is not a valid PDF, Word document, PNG or JPG.", null);

      DocumentRecord d = new DocumentRecord(appId, user.userId());
      d.name = name; d.docType = type; d.contentType = kind.contentType; d.sizeBytes = Files.size(target); d.storageKey = key;
      return toResponse(repo.saveAndFlush(d));
    } catch (RuntimeException | IOException e) {
      deleteQuietly(target);   // never leave an orphan file behind when the upload is refused or the save fails
      throw e;
    }
  }

  @GetMapping("/documents/{id}/download")
  public ResponseEntity<Resource> download(@AuthenticationPrincipal AuthUser user, @PathVariable long id) {
    DocumentRecord d = repo.findByIdAndUserId(id, user.userId()).orElseThrow(() -> ApiException.notFound("We could not find that document."));
    Path file = userDir(user.userId()).resolve(d.storageKey).normalize();
    if (!file.startsWith(userDir(user.userId())) || !Files.isRegularFile(file))
      throw ApiException.notFound("The stored file is missing. Remove this entry and upload it again.");
    return ResponseEntity.ok()
        .contentType(MediaType.parseMediaType(d.contentType))
        .contentLength(d.sizeBytes)
        .header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.attachment().filename(d.name, StandardCharsets.UTF_8).build().toString())
        .header(HttpHeaders.CACHE_CONTROL, "private, no-store")
        .header("X-Content-Type-Options", "nosniff")
        .body(new FileSystemResource(file));
  }

  @DeleteMapping("/documents/{id}") @ResponseStatus(HttpStatus.NO_CONTENT) @Transactional
  public void delete(@AuthenticationPrincipal AuthUser user, @PathVariable long id) {
    DocumentRecord d = repo.findByIdAndUserId(id, user.userId()).orElseThrow(() -> ApiException.notFound("We could not find that document."));
    repo.delete(d);
    deleteQuietly(userDir(user.userId()).resolve(d.storageKey).normalize());
  }

  private void requireApplication(AuthUser user, long appId) {
    apps.findByIdAndUserIdAndDeletedAtIsNull(appId, user.userId()).orElseThrow(() -> ApiException.notFound("We could not find that application."));
  }

  private Path userDir(long userId) { return root.resolve(Long.toString(userId)).normalize(); }

  private static ApiException tooLarge() { return ApiException.payloadTooLarge("Files must be 5 MB or smaller."); }

  private static void deleteQuietly(Path p) {
    try { Files.deleteIfExists(p); } catch (IOException e) { log.warn("Could not delete stored file {}", p.getFileName()); }
  }

  private static DocumentResponse toResponse(DocumentRecord d) {
    return new DocumentResponse(d.id, d.applicationId, d.name, d.docType, d.contentType, d.sizeBytes, d.uploadedAt);
  }
}
