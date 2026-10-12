package com.docket.calendar;

import java.time.Instant;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

public interface OAuthStateRepository extends JpaRepository<OAuthState, Long> {
  Optional<OAuthState> findByStateHash(String stateHash);
  @Transactional @Modifying @Query("delete from OAuthState s where s.expiresAt < :cutoff or s.userId = :userId")
  int purge(@Param("cutoff") Instant cutoff, @Param("userId") Long userId);
}
