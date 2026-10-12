package com.docket.goals;

import com.docket.applications.ApplicationRepository;
import com.docket.dashboard.Metrics;
import com.docket.security.AuthUser;
import com.docket.web.ApiException;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.Map;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

/**
 * Weekly application goal. Progress counts applications whose application date falls in the current
 * Monday-Sunday week (UTC), so undated entries and deleted applications are not counted.
 */
@RestController
@RequestMapping("/api/goals")
public class GoalController {
  public record GoalRequest(@NotNull(message = "Enter a target") @Min(value = 1, message = "Use at least 1") @Max(value = 200, message = "Use at most 200") Integer target) {}
  public record GoalResponse(Long id, Integer target, long submitted, LocalDate weekStart, LocalDate weekEnd) {}

  private final GoalRepository goals;
  private final ApplicationRepository apps;

  public GoalController(GoalRepository goals, ApplicationRepository apps) { this.goals = goals; this.apps = apps; }

  @GetMapping
  @Transactional(readOnly = true)
  public GoalResponse current(@AuthenticationPrincipal AuthUser user) {
    return response(user.userId(), goals.findByUserId(user.userId()).orElse(null));
  }

  @PostMapping @ResponseStatus(HttpStatus.CREATED)
  @Transactional
  public GoalResponse create(@AuthenticationPrincipal AuthUser user, @Valid @RequestBody GoalRequest body) {
    if (goals.findByUserId(user.userId()).isPresent())
      throw ApiException.conflict("You already have a weekly goal. Update it instead.", null);
    try {
      return response(user.userId(), goals.saveAndFlush(new WeeklyGoal(user.userId(), body.target())));
    } catch (DataIntegrityViolationException e) {   // two requests raced; the unique key on user_id decides
      throw ApiException.conflict("You already have a weekly goal. Update it instead.", null);
    }
  }

  @PutMapping("/{id}")
  @Transactional
  public GoalResponse update(@AuthenticationPrincipal AuthUser user, @PathVariable long id, @Valid @RequestBody GoalRequest body) {
    WeeklyGoal g = goals.findByIdAndUserId(id, user.userId()).orElseThrow(() -> ApiException.notFound("We could not find that goal."));
    g.target = body.target();
    return response(user.userId(), goals.save(g));
  }

  private GoalResponse response(long userId, WeeklyGoal g) {
    LocalDate start = Metrics.weekStart(LocalDate.now(ZoneOffset.UTC)), end = start.plusDays(6);
    long submitted = apps.countByUserIdAndDeletedAtIsNullAndApplicationDateBetween(userId, start, end);
    return new GoalResponse(g == null ? null : g.id, g == null ? null : g.target, submitted, start, end);
  }
}
