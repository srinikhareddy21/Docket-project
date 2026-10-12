package com.docket.web;

import java.util.Map;

/** Error body the frontend expects: { message, fieldErrors }. */
public record ApiError(String message, Map<String, String> fieldErrors) {
  public static ApiError of(String message) { return new ApiError(message, Map.of()); }
}
