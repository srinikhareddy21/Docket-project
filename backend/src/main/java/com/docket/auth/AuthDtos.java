package com.docket.auth;

import com.docket.user.User;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** Explicit request/response shapes, so clients can never set fields we did not list (no mass assignment). */
public final class AuthDtos {
  private AuthDtos() {}

  public record RegisterRequest(
      @NotBlank(message = "Enter your full name") @Size(min = 2, max = 100, message = "Name must be 2 to 100 characters") String name,
      @NotBlank(message = "Enter your email") @Email(message = "Enter a valid email address") @Size(max = 254) String email,
      @NotBlank(message = "Enter a password") @Size(max = 128, message = "Password is too long") String password,
      @AssertTrue(message = "Accept the terms and privacy policy to continue") boolean acceptTerms) {}

  public record LoginRequest(
      @NotBlank(message = "Enter your email") @Size(max = 254) String email,
      @NotBlank(message = "Enter your password") @Size(max = 128) String password,
      Boolean remember) {}

  public record ForgotPasswordRequest(
      @NotBlank(message = "Enter your email") @Email(message = "Enter a valid email address") @Size(max = 254) String email) {}

  public record ResetPasswordRequest(
      @NotBlank @Size(max = 200) String token,
      @NotBlank(message = "Enter a password") @Size(max = 128, message = "Password is too long") String password) {}

  public record UserResponse(Long id, String name, String email) {
    public static UserResponse from(User u) { return new UserResponse(u.getId(), u.getName(), u.getEmail()); }
  }
}
