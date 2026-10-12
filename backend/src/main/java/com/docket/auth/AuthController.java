package com.docket.auth;

import com.docket.auth.AuthDtos.*;
import com.docket.security.AuthUser;
import com.docket.security.CookieService;
import com.docket.web.ApiException;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/auth")
public class AuthController {
  private final AuthService auth;
  private final CookieService cookies;

  public AuthController(AuthService auth, CookieService cookies) { this.auth = auth; this.cookies = cookies; }

  /** Creates the account only. The user logs in next, so no session is issued here. */
  @PostMapping("/register") @ResponseStatus(HttpStatus.CREATED)
  public UserResponse register(@Valid @RequestBody RegisterRequest body) {
    return UserResponse.from(auth.register(body));
  }

  @PostMapping("/login")
  public UserResponse login(@Valid @RequestBody LoginRequest body, HttpServletResponse res) {
    AuthService.LoginResult result = auth.login(body);
    cookies.writeSession(res, result.session());
    return UserResponse.from(result.user());
  }

  @PostMapping("/refresh") @ResponseStatus(HttpStatus.NO_CONTENT)
  public void refresh(@CookieValue(name = CookieService.REFRESH, required = false) String refreshToken, HttpServletResponse res) {
    try {
      cookies.writeSession(res, auth.refresh(refreshToken));
    } catch (ApiException e) {
      cookies.clear(res);
      throw e;
    }
  }

  @PostMapping("/logout") @ResponseStatus(HttpStatus.NO_CONTENT)
  public void logout(@CookieValue(name = CookieService.REFRESH, required = false) String refreshToken,
                     @AuthenticationPrincipal AuthUser user, HttpServletResponse res) {
    auth.logout(refreshToken, user == null ? null : user.sid());
    cookies.clear(res);
  }

  @GetMapping("/me")
  public UserResponse me(@AuthenticationPrincipal AuthUser user) {
    return UserResponse.from(auth.currentUser(user.userId()));
  }

  /** Lets the SPA obtain the XSRF-TOKEN cookie before its first state-changing request. */
  @GetMapping("/csrf") @ResponseStatus(HttpStatus.NO_CONTENT)
  public void csrf() {}

  /** Always 202, whether or not the address belongs to an account. */
  @PostMapping("/forgot-password") @ResponseStatus(HttpStatus.ACCEPTED)
  public void forgot(@Valid @RequestBody ForgotPasswordRequest body) { auth.forgotPassword(body.email()); }

  @PostMapping("/reset-password") @ResponseStatus(HttpStatus.NO_CONTENT)
  public void reset(@Valid @RequestBody ResetPasswordRequest body) { auth.resetPassword(body); }
}
