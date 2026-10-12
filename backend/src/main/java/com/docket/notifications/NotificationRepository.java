package com.docket.notifications;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface NotificationRepository extends JpaRepository<Notification, Long> {
  Optional<Notification> findByIdAndUserId(Long id, Long userId);

  @Query("select n from Notification n where n.userId = :userId order by n.createdAt desc, n.id desc")
  List<Notification> recent(@Param("userId") Long userId, Pageable page);

  long countByUserIdAndReadAtIsNull(Long userId);

  List<Notification> findByUserIdAndDedupKeyIn(Long userId, Collection<String> keys);

  @Modifying(clearAutomatically = true, flushAutomatically = true)
  @Query("update Notification n set n.readAt = :now where n.userId = :userId and n.readAt is null")
  int markAllRead(@Param("userId") Long userId, @Param("now") Instant now);

  @Modifying(clearAutomatically = true, flushAutomatically = true)
  @Query("delete from Notification n where n.createdAt < :before")
  int purgeOlderThan(@Param("before") Instant before);
}
