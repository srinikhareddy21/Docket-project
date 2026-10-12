package com.docket.config;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.time.Duration;
import java.util.List;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/** All runtime configuration. Secrets come from environment variables, never from code. */
@Validated
@ConfigurationProperties(prefix = "docket")
public record AppProperties(
    @NotBlank String frontendUrl,
    @NotNull List<String> corsAllowedOrigins,
    @NotBlank String mailFrom,
    @Valid @NotNull Jwt jwt,
    @Valid @NotNull Cookie cookie,
    @Valid @NotNull Auth auth,
    @Valid @NotNull RateLimit rateLimit,
    @Valid @NotNull Applications applications,
    @Valid @NotNull Storage storage,
    @Valid @NotNull Google google,
    @Valid @NotNull Extension extension) {

  public record Jwt(@NotBlank String secret, @NotBlank String issuer) {}
  public record Cookie(boolean secure) {}
  public record Auth(@NotNull Duration accessTtl, @NotNull Duration refreshTtlRemember,
                     @NotNull Duration refreshTtlSession, @NotNull Duration resetTokenTtl) {}
  public record RateLimit(int ipMax, @NotNull Duration ipWindow, int loginFailureMax,
                          @NotNull Duration loginFailureWindow) {}
  /** undoWindow: how long after deleting an application it can be restored. deletedRetention: when it is purged for good. */
  public record Applications(@NotNull Duration undoWindow, @NotNull Duration deletedRetention) {}
  /** dir: where uploaded documents are stored on the server's disk. Keep it outside the web root and back it up. */
  public record Storage(@NotBlank String dir) {}

  /**
   * Google Calendar. All values are optional: with any of them blank the integration reports "not configured" and
   * nothing is faked. tokenEncryptionKey: base64 of exactly 32 random bytes (openssl rand -base64 32).
   */
  public record Google(String clientId, String clientSecret, String redirectUri, String tokenEncryptionKey) {
    public boolean configured() {
      return notBlank(clientId) && notBlank(clientSecret) && notBlank(redirectUri) && com.docket.calendar.TokenCrypto.isValidKey(tokenEncryptionKey);
    }
    private static boolean notBlank(String s) { return s != null && !s.isBlank(); }
  }
  /** allowedOrigins: browser-extension origins (chrome-extension://<id>) allowed to call /api/extension/*. May be empty. */
  public record Extension(@NotNull List<String> allowedOrigins) {}
}
