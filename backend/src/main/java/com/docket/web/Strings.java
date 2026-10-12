package com.docket.web;

public final class Strings {
  private Strings() {}
  /** Trims; returns null for null or blank so empty form fields are stored as missing, not as "". */
  public static String blank(String s) { return s == null || s.isBlank() ? null : s.trim(); }

  public static boolean validHttpUrl(String s) {
    try {
      java.net.URI u = new java.net.URI(s);
      return u.getScheme() != null && (u.getScheme().equalsIgnoreCase("http") || u.getScheme().equalsIgnoreCase("https"))
          && u.getHost() != null && !u.getHost().isBlank();
    } catch (Exception e) { return false; }
  }
}
