package com.docket.security;

import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.nio.charset.StandardCharsets;

/** Writes the standard error body from servlet filters (outside Spring MVC). Messages are constants. */
public final class JsonErrors {
  private JsonErrors() {}

  public static void write(HttpServletResponse res, int status, String message) throws IOException {
    res.setStatus(status);
    res.setContentType("application/json");
    res.setCharacterEncoding(StandardCharsets.UTF_8.name());
    res.getWriter().write("{\"message\":\"" + message.replace("\\", "\\\\").replace("\"", "\\\"") + "\",\"fieldErrors\":{}}");
  }
}
