package com.docket;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.docket.extension.ExtensionAuthFilter;
import org.junit.jupiter.api.Test;

/** Regression: extension path detection must stay aligned with CSRF ignore and authorize matchers. */
class ExtensionPathTest {
  @Test void extensionApiPathsAreDetected() {
    assertTrue(ExtensionAuthFilter.isExtensionPath("/api/extension/me"));
    assertTrue(ExtensionAuthFilter.isExtensionPath("/api/extension/applications"));
    assertTrue(ExtensionAuthFilter.isExtensionPath("/api/extension/duplicates"));
    assertTrue(ExtensionAuthFilter.isExtensionPath("/api/extension/applications;jsessionid=abc"));
  }

  @Test void nonExtensionPathsAreRejected() {
    assertFalse(ExtensionAuthFilter.isExtensionPath(null));
    assertFalse(ExtensionAuthFilter.isExtensionPath("/api/applications"));
    assertFalse(ExtensionAuthFilter.isExtensionPath("/api/extensions"));
    assertFalse(ExtensionAuthFilter.isExtensionPath("/api/extension")); // endpoints are under /api/extension/…
  }
}
