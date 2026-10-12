package com.docket.share;

import com.docket.security.AuthUser;
import com.docket.share.ShareService.*;
import java.util.List;
import java.util.Map;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api")
public class ShareController {
  public record CreateRequest(int expiresInDays) {}
  private final ShareService service;
  public ShareController(ShareService service) { this.service = service; }

  @GetMapping("/share-links")
  public List<LinkDto> list(@AuthenticationPrincipal AuthUser user) { return service.list(user.userId()); }

  @PostMapping("/share-links") @ResponseStatus(HttpStatus.CREATED)
  public LinkDto create(@AuthenticationPrincipal AuthUser user, @RequestBody CreateRequest body) { return service.create(user.userId(), body.expiresInDays()); }

  @DeleteMapping("/share-links/{id}") @ResponseStatus(HttpStatus.NO_CONTENT)
  public void revoke(@AuthenticationPrincipal AuthUser user, @PathVariable long id) { service.revoke(user.userId(), id); }

  /** Public, read-only, GET only. Cannot reach any other endpoint or record. */
  @GetMapping("/share/{token}")
  public ResponseEntity<PublicSummary> view(@PathVariable String token) {
    return ResponseEntity.ok().cacheControl(CacheControl.noStore()).header("X-Robots-Tag", "noindex, nofollow").body(service.publicView(token));
  }
}
