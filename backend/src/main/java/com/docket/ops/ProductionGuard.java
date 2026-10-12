package com.docket.ops;

import com.docket.config.AppProperties;
import java.net.URI;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

import jakarta.annotation.PostConstruct;

/**
 * With the "prod" profile active the server refuses to start on an unsafe configuration, instead of running
 * quietly with a weak secret or cookies that browsers would send over plain HTTP. The message names the
 * setting that is wrong, never its value.
 */
@Component
@Profile("prod")
public class ProductionGuard {
  private final AppProperties props;
  private final String mailHost;

  public ProductionGuard(AppProperties props, @Value("${spring.mail.host:}") String mailHost) { this.props = props; this.mailHost = mailHost; }

  @PostConstruct
  void verify() {
    List<String> problems = problems(props, mailHost);
    if (!problems.isEmpty()) throw new IllegalStateException("Unsafe production configuration:\n - " + String.join("\n - ", problems));
  }

  /**
   * A development mail catcher (or nothing) as the mail server means password-reset and reminder emails are silently
   * lost: the send fails, is logged, and the user simply never receives anything.
   */
  static List<String> problems(AppProperties p, String mailHost) {
    List<String> out = problems(p);
    String h = mailHost == null ? "" : mailHost.trim().toLowerCase();
    if (h.isEmpty() || h.equals("localhost") || h.equals("127.0.0.1") || h.equals("::1") || h.equals("mailhog"))
      out.add("MAIL_HOST must be a real SMTP server (it is empty or a local development mail catcher, so reset emails would never arrive)");
    return out;
  }

  /** Pure rule set, separate from startup so it can be checked on its own. */
  static List<String> problems(AppProperties p) {
    List<String> out = new ArrayList<>();
    String secret = p.jwt().secret();
    if (secret == null || secret.length() < 43 || decodedLength(secret) < 32) out.add("JWT_SECRET must be at least 32 random bytes (openssl rand -base64 48)");
    if (secret != null && looksLikePlaceholder(secret)) out.add("JWT_SECRET looks like a placeholder");
    if (!p.cookie().secure()) out.add("COOKIE_SECURE must be true so cookies are only sent over HTTPS");
    if (!isHttps(p.frontendUrl())) out.add("FRONTEND_URL must be an https:// address");
    for (String o : p.corsAllowedOrigins()) {
      if (o.contains("*")) out.add("CORS_ALLOWED_ORIGINS must not contain wildcards");
      else if (!isHttps(o)) out.add("CORS_ALLOWED_ORIGINS entries must be https:// origins");
    }
    for (String o : p.extension().allowedOrigins()) if (o.contains("*")) out.add("EXTENSION_ORIGINS must not contain wildcards");
    return out;
  }

  /** True if the secret, as written or after Base64 decoding, spells out an obvious placeholder word. */
  private static boolean looksLikePlaceholder(String secret) {
    List<String> words = List.of("change", "secret", "password", "example", "docket", "default");
    String raw = secret.toLowerCase();
    String decoded;
    try { decoded = new String(Base64.getDecoder().decode(secret.trim()), java.nio.charset.StandardCharsets.ISO_8859_1).toLowerCase(); }
    catch (IllegalArgumentException e) { decoded = ""; }
    for (String w : words) if (raw.contains(w) || decoded.contains(w)) return true;
    return false;
  }

  private static boolean isHttps(String url) {
    try { return url != null && "https".equalsIgnoreCase(URI.create(url.trim()).getScheme()); } catch (IllegalArgumentException e) { return false; }
  }

  private static int decodedLength(String s) {
    try { return Base64.getDecoder().decode(s.trim()).length; } catch (IllegalArgumentException e) { return s.length(); }
  }
}
