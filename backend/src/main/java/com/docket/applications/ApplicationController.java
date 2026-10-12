package com.docket.applications;

import com.docket.applications.ApplicationDtos.*;
import com.docket.security.AuthUser;
import jakarta.validation.Valid;
import java.util.List;
import java.util.Map;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import com.docket.web.ApiException;

@RestController
@RequestMapping("/api/applications")
public class ApplicationController {
  private final ApplicationService service;
  private final com.docket.autofill.AutofillService autofill;

  public ApplicationController(ApplicationService service, com.docket.autofill.AutofillService autofill) { this.service = service; this.autofill = autofill; }

  @GetMapping
  public PagedResponse<ApplicationResponse> list(@AuthenticationPrincipal AuthUser user,
      @RequestParam(required = false) String q, @RequestParam(required = false) String status,
      @RequestParam(required = false) String jobType, @RequestParam(required = false) String source,
      @RequestParam(required = false) String workMode, @RequestParam(required = false) String location,
      @RequestParam(required = false) String sort,
      @RequestParam(defaultValue = "0") int page, @RequestParam(defaultValue = "20") int size) {
    return service.list(user.userId(), new ApplicationService.Query(q, status, jobType, source, workMode, location, sort, page, size));
  }

  @PostMapping @ResponseStatus(HttpStatus.CREATED)
  public ApplicationResponse create(@AuthenticationPrincipal AuthUser user, @Valid @RequestBody ApplicationRequest body) {
    return service.create(user.userId(), body);
  }

  // Fixed paths first so they are never mistaken for an {id}.
  @GetMapping("/duplicates")
  public List<DuplicateMatch> duplicates(@AuthenticationPrincipal AuthUser user, @RequestParam(required = false) String company,
      @RequestParam(required = false) String position, @RequestParam(required = false) String url,
      @RequestParam(required = false) Long excludeId) {
    return service.duplicates(user.userId(), company, position, url, excludeId);
  }

  @GetMapping("/suggestions")
  public Suggestions suggestions(@AuthenticationPrincipal AuthUser user) { return service.suggestions(user.userId()); }

  @GetMapping("/export")
  public ResponseEntity<byte[]> export(@AuthenticationPrincipal AuthUser user) {
    return ResponseEntity.ok()
        .contentType(new MediaType("text", "csv", java.nio.charset.StandardCharsets.UTF_8))
        .header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.attachment().filename("docket-applications.csv").build().toString())
        .header(HttpHeaders.CACHE_CONTROL, "no-store")
        .body(service.exportCsv(user.userId()));
  }

  @PutMapping("/bulk-status")
  public BulkResult bulkStatus(@AuthenticationPrincipal AuthUser user, @Valid @RequestBody BulkStatusRequest body) {
    return service.bulkStatus(user.userId(), body);
  }

  /** Fetches the page server-side (SSRF-guarded) and returns only what the page itself states. Nothing is saved. */
  @PostMapping("/autofill")
  public com.docket.autofill.AutofillDtos.AutofillResponse autofill(@AuthenticationPrincipal AuthUser user,
      @Valid @RequestBody com.docket.autofill.AutofillDtos.AutofillRequest body) {
    return autofill.run(user.userId(), body.url());
  }

  @GetMapping("/{id}")
  public ApplicationResponse get(@AuthenticationPrincipal AuthUser user, @PathVariable long id) { return service.get(user.userId(), id); }

  @PutMapping("/{id}")
  public ApplicationResponse update(@AuthenticationPrincipal AuthUser user, @PathVariable long id, @Valid @RequestBody ApplicationRequest body) {
    return service.update(user.userId(), id, body);
  }

  @DeleteMapping("/{id}") @ResponseStatus(HttpStatus.NO_CONTENT)
  public void delete(@AuthenticationPrincipal AuthUser user, @PathVariable long id) { service.delete(user.userId(), id); }

  @PostMapping("/{id}/restore")
  public ApplicationResponse restore(@AuthenticationPrincipal AuthUser user, @PathVariable long id) { return service.restore(user.userId(), id); }

  @PutMapping("/{id}/status")
  public ApplicationResponse setStatus(@AuthenticationPrincipal AuthUser user, @PathVariable long id, @Valid @RequestBody StatusRequest body) {
    return service.setStatus(user.userId(), id, body);
  }

  @GetMapping("/{id}/history")
  public List<HistoryEntry> history(@AuthenticationPrincipal AuthUser user, @PathVariable long id) { return service.history(user.userId(), id); }
}
