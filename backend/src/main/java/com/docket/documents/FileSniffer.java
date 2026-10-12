package com.docket.documents;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;
import java.util.Set;
import java.util.zip.ZipFile;

/**
 * Decides what a file really is from its bytes, never from the name or the browser's Content-Type.
 * Allowed: PDF, Word (.doc, .docx), PNG, JPEG. Pure JDK, no framework dependencies.
 */
public final class FileSniffer {
  private FileSniffer() {}

  public enum Kind {
    PDF("application/pdf", Set.of("pdf")),
    PNG("image/png", Set.of("png")),
    JPEG("image/jpeg", Set.of("jpg", "jpeg")),
    DOC("application/msword", Set.of("doc")),
    DOCX("application/vnd.openxmlformats-officedocument.wordprocessingml.document", Set.of("docx"));

    public final String contentType;
    final Set<String> extensions;
    Kind(String contentType, Set<String> extensions) { this.contentType = contentType; this.extensions = extensions; }

    public boolean allowsExtension(String ext) { return ext != null && extensions.contains(ext.toLowerCase(Locale.ROOT)); }
  }

  /** Returns the detected kind, or null if the file is not one of the allowed types. */
  public static Kind detect(Path file) throws IOException {
    byte[] h = new byte[8];
    int n;
    try (InputStream in = Files.newInputStream(file)) { n = in.readNBytes(h, 0, h.length); }
    if (n >= 5 && h[0] == '%' && h[1] == 'P' && h[2] == 'D' && h[3] == 'F' && h[4] == '-') return Kind.PDF;
    if (n >= 8 && (h[0] & 0xFF) == 0x89 && h[1] == 'P' && h[2] == 'N' && h[3] == 'G' && h[4] == 0x0D && h[5] == 0x0A && h[6] == 0x1A && h[7] == 0x0A) return Kind.PNG;
    if (n >= 3 && (h[0] & 0xFF) == 0xFF && (h[1] & 0xFF) == 0xD8 && (h[2] & 0xFF) == 0xFF) return Kind.JPEG;
    if (n >= 8 && (h[0] & 0xFF) == 0xD0 && (h[1] & 0xFF) == 0xCF && (h[2] & 0xFF) == 0x11 && (h[3] & 0xFF) == 0xE0
        && (h[4] & 0xFF) == 0xA1 && (h[5] & 0xFF) == 0xB1 && (h[6] & 0xFF) == 0x1A && (h[7] & 0xFF) == 0xE1) return Kind.DOC;
    if (n >= 4 && h[0] == 'P' && h[1] == 'K' && h[2] == 0x03 && h[3] == 0x04) return isDocx(file) ? Kind.DOCX : null;
    return null;
  }

  /** A .docx is a zip with word/document.xml. Only the zip directory is read, so a decompression bomb cannot hurt. */
  private static boolean isDocx(Path file) {
    try (ZipFile z = new ZipFile(file.toFile())) {
      return z.size() <= 5000 && z.getEntry("[Content_Types].xml") != null && z.getEntry("word/document.xml") != null;
    } catch (IOException | RuntimeException e) {
      return false;
    }
  }

  /** Safe display name: no path parts, no control characters, bounded length. Never used to build a file path. */
  public static String displayName(String original) {
    String s = original == null ? "" : original;
    s = s.substring(Math.max(s.lastIndexOf('/'), s.lastIndexOf('\\')) + 1);
    s = s.replaceAll("[\\p{Cntrl}\\u202A-\\u202E\\u2066-\\u2069]", "").trim();
    if (s.length() > 150) {
      int dot = s.lastIndexOf('.');
      String ext = dot > 0 && s.length() - dot <= 10 ? s.substring(dot) : "";
      s = s.substring(0, 150 - ext.length()) + ext;
    }
    return s.isEmpty() ? "document" : s;
  }

  public static String extension(String name) {
    int dot = name == null ? -1 : name.lastIndexOf('.');
    return dot < 0 ? "" : name.substring(dot + 1);
  }
}
