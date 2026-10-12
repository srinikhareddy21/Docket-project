package com.docket.web;

import java.util.Map;
import org.springframework.http.HttpStatus;

/** Expected, user-facing failure. Message must be safe to show to the end user. */
public class ApiException extends RuntimeException {
  private final HttpStatus status;
  private final Map<String, String> fieldErrors;

  private ApiException(HttpStatus status, String message, Map<String, String> fieldErrors) {
    super(message);
    this.status = status;
    this.fieldErrors = fieldErrors == null ? Map.of() : fieldErrors;
  }
  public HttpStatus status() { return status; }
  public Map<String, String> fieldErrors() { return fieldErrors; }

  public static ApiException badRequest(String msg, Map<String, String> fields) { return new ApiException(HttpStatus.BAD_REQUEST, msg, fields); }
  public static ApiException unauthorized(String msg) { return new ApiException(HttpStatus.UNAUTHORIZED, msg, null); }
  public static ApiException conflict(String msg, Map<String, String> fields) { return new ApiException(HttpStatus.CONFLICT, msg, fields); }
  public static ApiException notFound(String msg) { return new ApiException(HttpStatus.NOT_FOUND, msg, null); }
  public static ApiException notImplemented(String msg) { return new ApiException(HttpStatus.NOT_IMPLEMENTED, msg, null); }
  public static ApiException payloadTooLarge(String msg) { return new ApiException(HttpStatus.valueOf(413), msg, null); }
  public static ApiException tooMany(String msg) { return new ApiException(HttpStatus.TOO_MANY_REQUESTS, msg, null); }
}
