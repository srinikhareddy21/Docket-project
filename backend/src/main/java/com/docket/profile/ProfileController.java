package com.docket.profile;

import com.docket.profile.ProfileDtos.*;
import com.docket.security.AuthUser;
import com.docket.security.CookieService;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api")
public class ProfileController {
  private final ProfileService service;
  private final CookieService cookies;

  public ProfileController(ProfileService service, CookieService cookies) { this.service = service; this.cookies = cookies; }

  @GetMapping("/profile")
  public ProfileResponse get(@AuthenticationPrincipal AuthUser user) { return ProfileResponse.from(service.get(user.userId())); }

  @PutMapping("/profile")
  public ProfileResponse update(@AuthenticationPrincipal AuthUser user, @Valid @RequestBody UpdateProfileRequest body) {
    return ProfileResponse.from(service.updateName(user.userId(), body.name()));
  }

  @PostMapping("/profile/password") @ResponseStatus(HttpStatus.NO_CONTENT)
  public void changePassword(@AuthenticationPrincipal AuthUser user, @Valid @RequestBody ChangePasswordRequest body) {
    service.changePassword(user.userId(), user.sid(), body.currentPassword(), body.newPassword());
  }

  /** Always 202 (unless the address is your own or you are over the limit), so it cannot reveal which emails have accounts. */
  @PostMapping("/profile/email-change") @ResponseStatus(HttpStatus.ACCEPTED)
  public void requestEmailChange(@AuthenticationPrincipal AuthUser user, @Valid @RequestBody EmailChangeRequest body) {
    service.requestEmailChange(user.userId(), body.email());
  }

  /** Public on purpose: the link is opened from an email, possibly in a browser that is not logged in. The token is the proof. */
  @PostMapping("/auth/confirm-email-change") @ResponseStatus(HttpStatus.NO_CONTENT)
  public void confirmEmailChange(@Valid @RequestBody ConfirmEmailRequest body, HttpServletResponse res) {
    service.confirmEmailChange(body.token());
    cookies.clear(res);
  }

  @PostMapping("/profile/delete-request") @ResponseStatus(HttpStatus.NO_CONTENT)
  public void deleteAccount(@AuthenticationPrincipal AuthUser user, @Valid @RequestBody DeleteAccountRequest body, HttpServletResponse res) {
    service.deleteAccount(user.userId(), body.password());
    cookies.clear(res);
  }
}
