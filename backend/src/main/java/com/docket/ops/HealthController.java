package com.docket.ops;

import java.util.Map;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Liveness/readiness probe for the load balancer and Docker. Public on purpose; it reveals only UP or DOWN,
 * never versions, hostnames, configuration or error details.
 */
@RestController
@RequestMapping("/api/health")
public class HealthController {
  private final JdbcTemplate jdbc;

  public HealthController(JdbcTemplate jdbc) { this.jdbc = jdbc; }

  @GetMapping
  public ResponseEntity<Map<String, String>> health() {
    try {
      Integer one = jdbc.queryForObject("select 1", Integer.class);
      if (one != null && one == 1) return ResponseEntity.ok().header("Cache-Control", "no-store").body(Map.of("status", "UP"));
    } catch (RuntimeException ignored) {
      // fall through: the database is not reachable
    }
    return ResponseEntity.status(503).header("Cache-Control", "no-store").body(Map.of("status", "DOWN"));
  }
}
