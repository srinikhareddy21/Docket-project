package com.docket.extension;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

public interface ExtensionTokenRepository extends JpaRepository<ExtensionToken, Long> {
  Optional<ExtensionToken> findByTokenHash(String tokenHash);
  Optional<ExtensionToken> findByIdAndUserId(Long id, Long userId);
  List<ExtensionToken> findByUserIdOrderByCreatedAtDesc(Long userId);

  @Query("select count(t) from ExtensionToken t where t.userId = :userId and t.revokedAt is null and t.expiresAt > :now")
  long countActive(@Param("userId") Long userId, @Param("now") Instant now);

  @Transactional @Modifying
  @Query("update ExtensionToken t set t.lastUsedAt = :now where t.id = :id")
  int touch(@Param("id") Long id, @Param("now") Instant now);

  @Transactional @Modifying
  @Query("delete from ExtensionToken t where t.expiresAt < :cutoff or t.revokedAt < :cutoff")
  int purgeBefore(@Param("cutoff") Instant cutoff);
}
