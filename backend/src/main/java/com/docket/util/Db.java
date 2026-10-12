package com.docket.util;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;

/**
 * DATETIME(6) columns hold UTC wall-clock values (Hibernate is configured with jdbc time_zone UTC).
 * JDBC-based repositories use these helpers so no JVM time-zone conversion ever sneaks in.
 */
public final class Db {
  private Db() {}
  public static LocalDateTime ts(Instant i) { return i == null ? null : LocalDateTime.ofInstant(i, ZoneOffset.UTC); }
  public static Instant instant(ResultSet rs, String col) throws SQLException {
    LocalDateTime l = rs.getObject(col, LocalDateTime.class);
    return l == null ? null : l.toInstant(ZoneOffset.UTC);
  }
}
