package com.docket.dashboard;

import com.docket.dashboard.Metrics.*;
import com.docket.security.AuthUser;
import java.util.List;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Read-only. Everything is derived from the signed-in user's own applications. */
@RestController
@RequestMapping("/api")
public class DashboardController {
  private final DashboardService service;
  public DashboardController(DashboardService service) { this.service = service; }

  @GetMapping("/dashboard")
  public Summary dashboard(@AuthenticationPrincipal AuthUser user) { return service.summary(user.userId()); }

  @GetMapping("/dashboard/actions")
  public List<Action> actions(@AuthenticationPrincipal AuthUser user) { return service.actions(user.userId()); }

  @GetMapping("/analytics/funnel")
  public List<Stage> funnel(@AuthenticationPrincipal AuthUser user) { return service.summary(user.userId()).funnel(); }

  @GetMapping("/analytics/sources")
  public List<SourceRow> sources(@AuthenticationPrincipal AuthUser user) { return service.summary(user.userId()).sources(); }

  @GetMapping("/analytics/trends")
  public List<Period> trends(@AuthenticationPrincipal AuthUser user) { return service.trends(user.userId()); }

  @GetMapping("/analytics/export")
  public ResponseEntity<byte[]> exportAnalysis(@AuthenticationPrincipal AuthUser user) {
    return ResponseEntity.ok()
        .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"docket-analysis.csv\"")
        .contentType(new MediaType("text", "csv"))
        .body(service.exportAnalysisCsv(user.userId()));
  }
}
