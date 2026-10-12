package com.docket.profile;

import com.docket.user.User;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.time.Instant;

public final class ProfileDtos {
  private ProfileDtos() {}

  public record ProfileResponse(Long id, String name, String email, boolean emailVerified, Instant createdAt) {
    public static ProfileResponse from(User u) { return new ProfileResponse(u.getId(), u.getName(), u.getEmail(), u.isEmailVerified(), u.getCreatedAt()); }
  }

  public record UpdateProfileRequest(
      @NotBlank(message = "Enter your full name") @Size(min = 2, max = 100, message = "Name must be 2 to 100 characters") String name) {}

  public record ChangePasswordRequest(
      @NotBlank(message = "Enter your current password") @Size(max = 128) String currentPassword,
      @NotBlank(message = "Enter a new password") @Size(max = 128, message = "Password is too long") String newPassword) {}

  public record EmailChangeRequest(
      @NotBlank(message = "Enter the new email") @Email(message = "Enter a valid email address") @Size(max = 254) String email) {}

  public record ConfirmEmailRequest(@NotBlank @Size(max = 200) String token) {}

  public record DeleteAccountRequest(@NotBlank(message = "Enter your password") @Size(max = 128) String password) {}
}
