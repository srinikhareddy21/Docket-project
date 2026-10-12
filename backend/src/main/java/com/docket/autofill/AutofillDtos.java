package com.docket.autofill;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.util.List;
import java.util.Map;

public final class AutofillDtos {
  private AutofillDtos() {}

  public record AutofillRequest(@NotBlank(message = "Paste a job posting link") @Size(max = 2048, message = "This link is too long") String url) {}

  /**
   * found=false means the page was reached (or could not be) and nothing was extracted; fields is then empty.
   * reason is a stable code the UI can switch on. Nothing here is ever guessed: fields holds only what the page stated.
   */
  public record AutofillResponse(boolean found, String reason, String message, Map<String, Object> fields,
                                 List<String> extractedFields, List<String> warnings, String source) {
    static AutofillResponse notFound(String reason, String message) {
      return new AutofillResponse(false, reason, message, Map.of(), List.of(), List.of(), null);
    }
  }
}
