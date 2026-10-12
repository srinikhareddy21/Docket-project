package com.docket.checklist;

import com.docket.applications.ApplicationRepository;
import com.docket.interviews.InterviewRepository;
import com.docket.security.AuthUser;
import com.docket.web.ApiException;
import com.docket.web.Strings;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.util.List;
import java.util.Set;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api")
public class ChecklistController {
  private static final Set<String> REQUIREMENT_CATEGORIES =
      Set.of("Skills I have", "Skills to improve", "Documents to prepare", "Topics to revise", "Other");
  private static final int MAX_PER_APPLICATION = 200;

  @JsonIgnoreProperties(ignoreUnknown = true)
  public record AddRequest(
      @NotBlank(message = "Choose a list") @Pattern(regexp = "PREP|REQUIREMENT", message = "Choose a valid list") String type,
      @Size(max = 40) String category,
      @NotBlank(message = "Enter an item") @Size(max = 300, message = "Keep the item under 300 characters") String text,
      Long interviewId) {}

  @JsonIgnoreProperties(ignoreUnknown = true)
  public record PatchRequest(Boolean completed, @Size(max = 300, message = "Keep the item under 300 characters") String text, @Size(max = 40) String category) {}

  public record ItemResponse(Long id, Long applicationId, Long interviewId, String type, String category, String text, boolean completed) {}

  private final ChecklistRepository repo;
  private final ApplicationRepository apps;
  private final InterviewRepository interviews;
  public ChecklistController(ChecklistRepository repo, ApplicationRepository apps, InterviewRepository interviews) {
    this.repo = repo; this.apps = apps; this.interviews = interviews;
  }

  @GetMapping("/applications/{appId}/checklist") @Transactional(readOnly = true)
  public List<ItemResponse> list(@AuthenticationPrincipal AuthUser user, @PathVariable long appId) {
    requireApplication(user, appId);
    return repo.findByApplicationIdAndUserIdOrderByIdAsc(appId, user.userId()).stream().map(ChecklistController::toResponse).toList();
  }

  @PostMapping("/applications/{appId}/checklist") @ResponseStatus(HttpStatus.CREATED) @Transactional
  public ItemResponse add(@AuthenticationPrincipal AuthUser user, @PathVariable long appId, @Valid @RequestBody AddRequest body) {
    requireApplication(user, appId);
    if (repo.countByApplicationIdAndUserId(appId, user.userId()) >= MAX_PER_APPLICATION)
      throw ApiException.badRequest("This application already has the maximum of " + MAX_PER_APPLICATION + " checklist items.", null);
    ChecklistItem item = new ChecklistItem(appId, user.userId(), body.type());
    item.text = body.text().trim();
    item.category = category(body.type(), body.category());
    if (body.interviewId() != null) {
      if (!"PREP".equals(body.type())) throw ApiException.badRequest("Only preparation items can belong to an interview.", null);
      boolean ok = interviews.findWithApplication(user.userId(), appId).stream()
          .anyMatch(r -> ((com.docket.interviews.Interview) r[0]).getId().equals(body.interviewId()));
      if (!ok) throw ApiException.notFound("We could not find that interview.");
      item.interviewId = body.interviewId();
    }
    return toResponse(repo.save(item));
  }

  /** Toggle completion, or edit the text or category. Only the fields sent are changed. */
  @PatchMapping("/checklist/{id}") @Transactional
  public ItemResponse patch(@AuthenticationPrincipal AuthUser user, @PathVariable long id, @Valid @RequestBody PatchRequest body) {
    ChecklistItem item = repo.findByIdAndUserId(id, user.userId()).orElseThrow(() -> ApiException.notFound("We could not find that item."));
    if (body.completed() != null) item.completed = body.completed();
    if (body.text() != null) {
      String t = body.text().trim();
      if (t.isEmpty()) throw ApiException.badRequest("Enter an item", java.util.Map.of("text", "Enter an item"));
      item.text = t;
    }
    if (body.category() != null) item.category = category(item.type, body.category());
    return toResponse(repo.save(item));
  }

  @DeleteMapping("/checklist/{id}") @ResponseStatus(HttpStatus.NO_CONTENT) @Transactional
  public void delete(@AuthenticationPrincipal AuthUser user, @PathVariable long id) {
    repo.delete(repo.findByIdAndUserId(id, user.userId()).orElseThrow(() -> ApiException.notFound("We could not find that item.")));
  }

  private void requireApplication(AuthUser user, long appId) {
    apps.findByIdAndUserIdAndDeletedAtIsNull(appId, user.userId()).orElseThrow(() -> ApiException.notFound("We could not find that application."));
  }

  /** Requirement items are filed under one of the five categories; preparation items have none. */
  private static String category(String type, String raw) {
    String c = Strings.blank(raw);
    if (!"REQUIREMENT".equals(type) || c == null) return null;
    if (!REQUIREMENT_CATEGORIES.contains(c)) throw ApiException.badRequest("Choose a valid category.", java.util.Map.of("category", "Choose a valid category"));
    return c;
  }

  private static ItemResponse toResponse(ChecklistItem i) {
    return new ItemResponse(i.id, i.applicationId, i.interviewId, i.type, i.category, i.text, i.completed);
  }
}
