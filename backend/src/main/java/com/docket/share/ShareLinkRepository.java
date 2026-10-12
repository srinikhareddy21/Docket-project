package com.docket.share;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ShareLinkRepository extends JpaRepository<ShareLink, Long> {
  Optional<ShareLink> findByTokenHash(String tokenHash);
  Optional<ShareLink> findByIdAndUserId(Long id, Long userId);
  List<ShareLink> findByUserIdOrderByCreatedAtDesc(Long userId);

  @Query("select count(s) from ShareLink s where s.userId = :userId and s.revokedAt is null and s.expiresAt > :now")
  long countActive(@Param("userId") Long userId, @Param("now") Instant now);

  @Modifying
  @Query("delete from ShareLink s where s.expiresAt < :cutoff or s.revokedAt < :cutoff")
  int purgeBefore(@Param("cutoff") Instant cutoff);
}
