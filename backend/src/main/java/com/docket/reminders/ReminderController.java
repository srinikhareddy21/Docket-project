package com.docket.reminders;

import com.docket.reminders.ReminderService.*;
import com.docket.security.AuthUser;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/reminders")
public class ReminderController {
  private final ReminderService service;
  public ReminderController(ReminderService service) { this.service = service; }

  @GetMapping
  public List<ReminderDto> list(@AuthenticationPrincipal AuthUser user) { return service.list(user.userId()); }

  @PostMapping @ResponseStatus(HttpStatus.CREATED)
  public ReminderDto create(@AuthenticationPrincipal AuthUser user, @RequestBody CreateRequest body) { return service.createCustom(user.userId(), body); }

  @PutMapping("/{id}")
  public ReminderDto update(@AuthenticationPrincipal AuthUser user, @PathVariable long id, @RequestBody UpdateRequest body) {
    return service.update(user.userId(), id, body);
  }

  // Fixed paths below are not mistaken for {id}: Spring prefers the literal match.
  @GetMapping("/preferences")
  public PrefsDto prefs(@AuthenticationPrincipal AuthUser user) { return service.prefs(user.userId()); }

  @PutMapping("/preferences")
  public PrefsDto savePrefs(@AuthenticationPrincipal AuthUser user, @Valid @RequestBody PrefsDto body) { return service.savePrefs(user.userId(), body); }
}
