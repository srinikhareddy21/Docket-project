package com.docket.calendar;

import com.docket.calendar.CalendarService.*;
import com.docket.config.AppProperties;
import com.docket.security.AuthUser;
import java.net.URI;
import java.util.Map;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/integrations/google-calendar")
public class CalendarController {
  private final CalendarService service;
  private final AppProperties props;
  public CalendarController(CalendarService service, AppProperties props) { this.service = service; this.props = props; }

  @GetMapping public StatusDto status(@AuthenticationPrincipal AuthUser user) { return service.status(user.userId()); }

  /** Returns the Google consent URL. The browser is sent there by the app; nothing is connected until Google calls back. */
  @PostMapping("/connect") public Map<String, String> connect(@AuthenticationPrincipal AuthUser user) { return Map.of("url", service.startConnect(user.userId())); }

  @PutMapping("/preferences") public StatusDto prefs(@AuthenticationPrincipal AuthUser user, @RequestBody PrefsRequest body) { return service.savePrefs(user.userId(), body); }

  @PostMapping("/sync") public StatusDto sync(@AuthenticationPrincipal AuthUser user) { return service.syncNow(user.userId()); }

  @DeleteMapping public DisconnectResult disconnect(@AuthenticationPrincipal AuthUser user, @RequestParam(defaultValue = "false") boolean removeEvents) {
    return service.disconnect(user.userId(), removeEvents);
  }

  /** Public by necessity (Google redirects the browser here). Safe: it only acts on a valid, unexpired, single-use state we issued. */
  @GetMapping("/callback")
  public ResponseEntity<Void> callback(@RequestParam(required = false) String code, @RequestParam(required = false) String state, @RequestParam(required = false) String error) {
    String result = service.finishConnect(code, state, error);
    String base = props.frontendUrl().replaceAll("/+$", "");
    return ResponseEntity.status(302).header(HttpHeaders.LOCATION, URI.create(base + "/app/settings?google=" + result).toString())
        .header(HttpHeaders.CACHE_CONTROL, "no-store").header("Referrer-Policy", "no-referrer").build();
  }
}
