package com.docket.auth;

import java.time.Instant;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface RefreshTokenRepository extends JpaRepository<RefreshToken, Long> {
  Optional<RefreshToken> findByTokenHash(String tokenHash);

  /** A session (family) is alive while at least one of its tokens is unrevoked and unexpired. */
  @Query("select count(t) from RefreshToken t where t.familyId = :family and t.userId = :uid and t.revokedAt is null and t.expiresAt > :now")
  long countActive(@Param("family") String family, @Param("uid") Long uid, @Param("now") Instant now);

  @Modifying(clearAutomatically = true, flushAutomatically = true)
  @Query("update RefreshToken t set t.revokedAt = :now where t.familyId = :family and t.revokedAt is null")
  int revokeFamily(@Param("family") String family, @Param("now") Instant now);

  @Modifying(clearAutomatically = true, flushAutomatically = true)
  @Query("update RefreshToken t set t.revokedAt = :now where t.userId = :uid and t.revokedAt is null")
  int revokeAllForUser(@Param("uid") Long uid, @Param("now") Instant now);

  /** Signs out every other device but keeps the session identified by `family`. */
  @Modifying(clearAutomatically = true, flushAutomatically = true)
  @Query("update RefreshToken t set t.revokedAt = :now where t.userId = :uid and t.familyId <> :family and t.revokedAt is null")
  int revokeOthers(@Param("uid") Long uid, @Param("family") String family, @Param("now") Instant now);

  long deleteByExpiresAtBefore(Instant cutoff);
}
