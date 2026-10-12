package com.docket.applications;

import java.nio.charset.StandardCharsets;
import java.util.List;

/** Minimal RFC 4180 writer with spreadsheet formula-injection protection. */
public final class Csv {
  private Csv() {}

  public static String cell(String v) {
    if (v == null) return "";
    String s = v;
    // A cell starting with = + - @ (or tab/CR) can run as a formula when opened in Excel or Sheets.
    if (!s.isEmpty() && "=+-@\t\r".indexOf(s.charAt(0)) >= 0) s = "'" + s;
    if (s.contains(",") || s.contains("\"") || s.contains("\n") || s.contains("\r")) s = "\"" + s.replace("\"", "\"\"") + "\"";
    return s;
  }

  public static String row(List<String> cells) {
    StringBuilder b = new StringBuilder();
    for (int i = 0; i < cells.size(); i++) { if (i > 0) b.append(','); b.append(cell(cells.get(i))); }
    return b.append("\r\n").toString();
  }

  /** UTF-8 with a byte-order mark so Excel reads non-ASCII names correctly. */
  public static byte[] bytes(String csv) {
    byte[] body = csv.getBytes(StandardCharsets.UTF_8);
    byte[] out = new byte[body.length + 3];
    out[0] = (byte) 0xEF; out[1] = (byte) 0xBB; out[2] = (byte) 0xBF;
    System.arraycopy(body, 0, out, 3, body.length);
    return out;
  }
}
