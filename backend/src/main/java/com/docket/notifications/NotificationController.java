package com.docket.notifications;

import com.docket.notifications.NotificationService.Feed;
import com.docket.notifications.NotificationService.NotificationDto;
import com.docket.security.AuthUser;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/notifications")
public class NotificationController {
  private final NotificationService service;
  public NotificationController(NotificationService service) { this.service = service; }

  @GetMapping
  public Feed list(@AuthenticationPrincipal AuthUser user) {
    service.syncQuietly(user.userId());
    return service.list(user.userId());
  }

  @PostMapping("/{id}/read")
  public NotificationDto markRead(@AuthenticationPrincipal AuthUser user, @PathVariable long id) {
    return service.markRead(user.userId(), id);
  }

  @PostMapping("/read-all")
  public java.util.Map<String, Integer> markAllRead(@AuthenticationPrincipal AuthUser user) {
    return java.util.Map.of("marked", service.markAllRead(user.userId()));
  }
}
