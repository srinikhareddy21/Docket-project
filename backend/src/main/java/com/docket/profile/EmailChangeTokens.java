package com.docket.profile;

import com.docket.util.Db;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

/** Pending email changes. Only the SHA-256 of the emailed token is stored. */
@Repository
public class EmailChangeTokens {
  public record Row(long id, long userId, String newEmail, Instant expiresAt, Instant usedAt) {}

  private final JdbcTemplate jdbc;
  public EmailChangeTokens(JdbcTemplate jdbc) { this.jdbc = jdbc; }

  public void replace(long userId, String newEmail, String tokenHash, Instant expiresAt) {
    jdbc.update("DELETE FROM email_change_tokens WHERE user_id = ?", userId);
    jdbc.update("INSERT INTO email_change_tokens (user_id, new_email, token_hash, expires_at, created_at) VALUES (?,?,?,?,?)",
        userId, newEmail, tokenHash, Db.ts(expiresAt), Db.ts(Instant.now()));
  }

  public Optional<Row> findByHash(String tokenHash) {
    List<Row> rows = jdbc.query("SELECT token_id, user_id, new_email, expires_at, used_at FROM email_change_tokens WHERE token_hash = ?",
        (rs, i) -> new Row(rs.getLong("token_id"), rs.getLong("user_id"), rs.getString("new_email"), Db.instant(rs, "expires_at"), Db.instant(rs, "used_at")), tokenHash);
    return rows.stream().findFirst();
  }

  public void deleteForUser(long userId) { jdbc.update("DELETE FROM email_change_tokens WHERE user_id = ?", userId); }
  public int deleteExpired(Instant cutoff) { return jdbc.update("DELETE FROM email_change_tokens WHERE expires_at < ?", Db.ts(cutoff)); }
}
