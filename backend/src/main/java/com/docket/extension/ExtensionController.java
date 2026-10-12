package com.docket.extension;

import com.docket.applications.ApplicationDtos.*;
import com.docket.applications.ApplicationService;
import com.docket.extension.ExtensionTokenService.*;
import com.docket.security.AuthUser;
import com.docket.security.SlidingWindowLimiter;
import com.docket.user.UserRepository;
import com.docket.web.ApiException;
import jakarta.validation.Valid;
import java.util.List;
import java.util.Map;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api")
public class ExtensionController {
  public record CreateTokenRequest(String label) {}
  private final ExtensionTokenService tokens;
  private final ApplicationService apps;
  private final UserRepository users;
  private final SlidingWindowLimiter limiter;

  public ExtensionController(ExtensionTokenService tokens, ApplicationService apps, UserRepository users, @Qualifier("extensionLimiter") SlidingWindowLimiter limiter) {
    this.tokens = tokens; this.apps = apps; this.users = users; this.limiter = limiter;
  }

  // ----- managed from the DOCKET web app (cookie login + CSRF) -----

  @GetMapping("/extension-tokens") public List<TokenDto> list(@AuthenticationPrincipal AuthUser user) { return tokens.list(user.userId()); }

  @PostMapping("/extension-tokens") @ResponseStatus(HttpStatus.CREATED)
  public CreatedDto create(@AuthenticationPrincipal AuthUser user, @RequestBody CreateTokenRequest body) { return tokens.create(user.userId(), body.label()); }

  @DeleteMapping("/extension-tokens/{id}") @ResponseStatus(HttpStatus.NO_CONTENT)
  public void revoke(@AuthenticationPrincipal AuthUser user, @PathVariable long id) { tokens.revoke(user.userId(), id); }

  // ----- called by the Chrome extension (Bearer token only) -----

  @GetMapping("/extension/me")
  public Map<String, Object> me(@AuthenticationPrincipal AuthUser user) {
    throttle(user);
    return users.findById(user.userId()).map(u -> Map.<String, Object>of("name", u.getName())).orElseThrow(() -> ApiException.unauthorized("Account not found."));
  }

  @GetMapping("/extension/duplicates")
  public List<DuplicateMatch> duplicates(@AuthenticationPrincipal AuthUser user, @RequestParam(required = false) String company,
                                         @RequestParam(required = false) String position, @RequestParam(required = false) String url) {
    throttle(user);
    return apps.duplicates(user.userId(), company, position, url, null);
  }

  /** Creates the application only when the extension sends this request, which the user confirms in the popup. */
  @PostMapping("/extension/applications") @ResponseStatus(HttpStatus.CREATED)
  public ApplicationResponse create(@AuthenticationPrincipal AuthUser user, @Valid @RequestBody ApplicationRequest body) {
    throttle(user);
    return apps.create(user.userId(), body);
  }

  private void throttle(AuthUser user) {
    if (!limiter.tryAcquire("x" + user.userId())) throw ApiException.tooMany("Too many requests from the extension. Wait a minute and try again.");
  }
}
