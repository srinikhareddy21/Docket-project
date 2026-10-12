package com.docket.applications;

import java.time.Instant;
import java.time.LocalDate;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/**
 * Every method takes the owner's id. There is deliberately no way to load an application by id alone,
 * so a guessed or tampered id can never reach another user's record.
 * Optional filters are passed as empty strings when absent.
 */
public interface ApplicationRepository extends JpaRepository<Application, Long> {

  Optional<Application> findByIdAndUserIdAndDeletedAtIsNull(Long id, Long userId);

  /** Includes soft-deleted rows. Used only by restore. */
  Optional<Application> findByIdAndUserId(Long id, Long userId);

  List<Application> findByIdInAndUserIdAndDeletedAtIsNull(Collection<Long> ids, Long userId);

  List<Application> findByUserIdAndDeletedAtIsNullOrderByApplicationDateDescIdDesc(Long userId);

  @Query("""
      select a from Application a
      where a.userId = :userId and a.deletedAt is null
        and (:status = '' or a.currentStatus = :status)
        and (:jobType = '' or a.jobType = :jobType)
        and (:source = '' or a.applicationSource = :source)
        and (:workMode = '' or a.workMode = :workMode)
        and (:location = '' or lower(a.location) like :location escape '!')
        and (:q = '' or lower(a.companyName) like :q escape '!'
                     or lower(a.position) like :q escape '!'
                     or lower(a.jobDescription) like :q escape '!'
                     or lower(a.notes) like :q escape '!'
                     or exists (select 1 from Interview i where i.applicationId = a.id and i.userId = a.userId
                                and (lower(i.preparationNotes) like :q escape '!' or lower(i.topicsToRevise) like :q escape '!'
                                     or lower(i.questionsToAsk) like :q escape '!' or lower(i.feedback) like :q escape '!')))
      """)
  Page<Application> search(@Param("userId") Long userId, @Param("status") String status, @Param("jobType") String jobType,
                           @Param("source") String source, @Param("workMode") String workMode,
                           @Param("location") String location, @Param("q") String q, Pageable pageable);

  @Query("""
      select a from Application a
      where a.userId = :userId and a.deletedAt is null
        and (:excludeId = 0L or a.id <> :excludeId)
        and ((:linkKey <> '' and a.linkKey = :linkKey)
          or (:companyKey <> '' and :positionKey <> '' and a.companyKey = :companyKey and a.positionKey = :positionKey))
      order by a.createdAt desc
      """)
  List<Application> findDuplicates(@Param("userId") Long userId, @Param("linkKey") String linkKey,
                                   @Param("companyKey") String companyKey, @Param("positionKey") String positionKey,
                                   @Param("excludeId") long excludeId, Pageable pageable);

  @Query("select a.companyName from Application a where a.userId = :userId and a.deletedAt is null group by a.companyName order by max(a.createdAt) desc")
  List<String> recentCompanies(@Param("userId") Long userId, Pageable pageable);

  @Query("select a.position from Application a where a.userId = :userId and a.deletedAt is null group by a.position order by max(a.createdAt) desc")
  List<String> recentRoles(@Param("userId") Long userId, Pageable pageable);

  /** Retention purge. Dependent rows go with it through ON DELETE CASCADE foreign keys. */
  @Modifying
  @Query("delete from Application a where a.deletedAt is not null and a.deletedAt < :cutoff")
  int purgeDeletedBefore(@Param("cutoff") Instant cutoff);

  /** Applications dated within [from, to], inclusive. Feeds the weekly goal. */
  long countByUserIdAndDeletedAtIsNullAndApplicationDateBetween(Long userId, LocalDate from, LocalDate to);
}
