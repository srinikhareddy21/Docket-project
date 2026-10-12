package com.docket.skills;

import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface SkillRepository extends JpaRepository<Skill, Long> {
  List<Skill> findByUserIdOrderByCategoryAscNameAsc(Long userId);
  Optional<Skill> findByIdAndUserId(Long id, Long userId);
  boolean existsByUserIdAndNameKey(Long userId, String nameKey);
  long countByUserId(Long userId);

  @Query("select s.name from Skill s where s.userId = :userId")
  List<String> names(@Param("userId") Long userId);
}
