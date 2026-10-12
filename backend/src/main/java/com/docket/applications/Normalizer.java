package com.docket.applications;

import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.text.Normalizer.Form;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HexFormat;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/** Normalization used for duplicate detection. Pure functions, no framework dependencies. */
public final class Normalizer {
  private Normalizer() {}

  /** Query parameters that identify a visit or campaign, not a job. */
  private static final Set<String> TRACKING = Set.of("ref", "refid", "trk", "trackingid", "gclid", "fbclid", "msclkid",
      "mc_cid", "mc_eid", "source", "src", "referrer", "lipi", "midtoken", "midsig", "eid");

  /** "  Acme, Inc. " and "acme inc" produce the same key. */
  public static String text(String s) {
    if (s == null) return "";
    String n = java.text.Normalizer.normalize(s, Form.NFKD).replaceAll("\\p{M}+", "");
    n = n.toLowerCase(Locale.ROOT).replaceAll("[^\\p{L}\\p{N}]+", " ").trim().replaceAll("\\s+", " ");
    return n.length() > 160 ? n.substring(0, 160) : n;
  }

  /**
   * Canonical form of a job URL: host lower-cased without "www.", no scheme, fragment or trailing slash,
   * tracking parameters removed, remaining parameters sorted. Returns "" if the value is not a usable http(s) URL.
   */
  public static String url(String raw) {
    if (raw == null || raw.isBlank()) return "";
    try {
      URI u = new URI(raw.trim());
      String scheme = u.getScheme();
      if (scheme == null || !(scheme.equalsIgnoreCase("http") || scheme.equalsIgnoreCase("https"))) return "";
      String host = u.getHost();
      if (host == null || host.isBlank()) return "";
      host = host.toLowerCase(Locale.ROOT);
      if (host.startsWith("www.")) host = host.substring(4);
      String path = u.getRawPath() == null ? "" : u.getRawPath();
      while (path.endsWith("/")) path = path.substring(0, path.length() - 1);
      List<String> kept = new ArrayList<>();
      if (u.getRawQuery() != null) {
        for (String p : u.getRawQuery().split("&")) {
          if (p.isEmpty()) continue;
          String key = p.contains("=") ? p.substring(0, p.indexOf('=')) : p;
          String k = key.toLowerCase(Locale.ROOT);
          if (k.startsWith("utm_") || TRACKING.contains(k)) continue;
          kept.add(p);
        }
        Collections.sort(kept);
      }
      return host + path + (kept.isEmpty() ? "" : "?" + String.join("&", kept));
    } catch (Exception e) {
      return "";
    }
  }

  /** Fixed-width key for indexing: SHA-256 hex of the normalized URL, or "" when there is none. */
  public static String urlKey(String raw) {
    String n = url(raw);
    if (n.isEmpty()) return "";
    try {
      return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(n.getBytes(StandardCharsets.UTF_8)));
    } catch (Exception e) {
      throw new IllegalStateException(e);
    }
  }

  /** Escapes LIKE wildcards using '!' as the escape character. */
  public static String likePattern(String q) {
    if (q == null || q.isBlank()) return "";
    String s = q.trim().toLowerCase(Locale.ROOT).replace("!", "!!").replace("%", "!%").replace("_", "!_");
    return "%" + s + "%";
  }
}
