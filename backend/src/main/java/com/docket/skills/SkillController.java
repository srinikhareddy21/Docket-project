package com.docket.skills;

import com.docket.security.AuthUser;
import com.docket.web.ApiException;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.util.List;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/skills")
public class SkillController {
  static final String CATEGORY_REGEX = "Programming languages|Web development|Databases|Tools and platforms|Core computer science|Communication and professional";
  private static final int MAX_SKILLS = 300;

  @JsonIgnoreProperties(ignoreUnknown = true)
  public record SkillRequest(
      @NotBlank(message = "Enter a skill") @Size(max = 80, message = "Keep the skill under 80 characters") String name,
      @NotBlank(message = "Choose a category") @Pattern(regexp = CATEGORY_REGEX, message = "Choose a valid category") String category) {}

  public record SkillResponse(Long id, String name, String category) {}

  private final SkillRepository repo;
  public SkillController(SkillRepository repo) { this.repo = repo; }

  @GetMapping
  public List<SkillResponse> list(@AuthenticationPrincipal AuthUser user) {
    return repo.findByUserIdOrderByCategoryAscNameAsc(user.userId()).stream().map(s -> new SkillResponse(s.id, s.name, s.category)).toList();
  }

  @PostMapping @ResponseStatus(HttpStatus.CREATED)
  public SkillResponse add(@AuthenticationPrincipal AuthUser user, @Valid @RequestBody SkillRequest body) {
    String name = body.name().trim().replaceAll("\\s+", " ");
    String key = SkillMatcher.normalize(name);
    if (key.isEmpty()) throw ApiException.badRequest("Enter a skill", java.util.Map.of("name", "Enter a skill"));
    if (repo.countByUserId(user.userId()) >= MAX_SKILLS) throw ApiException.badRequest("You have reached the limit of " + MAX_SKILLS + " skills.", null);
    if (repo.existsByUserIdAndNameKey(user.userId(), key)) throw ApiException.conflict("You already have that skill.", null);
    try {
      Skill s = repo.saveAndFlush(new Skill(user.userId(), name, key, body.category()));
      return new SkillResponse(s.id, s.name, s.category);
    } catch (DataIntegrityViolationException race) {   // two identical requests at once; the unique key decides
      throw ApiException.conflict("You already have that skill.", null);
    }
  }

  @DeleteMapping("/{id}") @ResponseStatus(HttpStatus.NO_CONTENT)
  public void remove(@AuthenticationPrincipal AuthUser user, @PathVariable long id) {
    Skill s = repo.findByIdAndUserId(id, user.userId()).orElseThrow(() -> ApiException.notFound("We could not find that skill."));
    repo.delete(s);
  }
}
