package com.docket.auth;

import java.nio.charset.StandardCharsets;
import java.util.regex.Pattern;

/** Server-side mirror of the frontend rules. The backend is the source of truth. */
public final class PasswordPolicy {
  private static final Pattern UPPER = Pattern.compile("[A-Z]");
  private static final Pattern LOWER = Pattern.compile("[a-z]");
  private static final Pattern DIGIT = Pattern.compile("[0-9]");
  private static final Pattern SYMBOL = Pattern.compile("[^A-Za-z0-9]");
  private PasswordPolicy() {}

  /** @return a user-facing message, or null when the password is acceptable. */
  public static String violation(String p) {
    if (p == null || p.length() < 10 || !UPPER.matcher(p).find() || !LOWER.matcher(p).find()
        || !DIGIT.matcher(p).find() || !SYMBOL.matcher(p).find()) {
      return "Password does not meet all requirements";
    }
    // BCrypt only uses the first 72 bytes; refuse longer input instead of silently truncating.
    if (p.getBytes(StandardCharsets.UTF_8).length > 72) return "Password must be 72 bytes or fewer";
    return null;
  }
}
