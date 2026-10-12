package com.docket.goals;

import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface GoalRepository extends JpaRepository<WeeklyGoal, Long> {
  Optional<WeeklyGoal> findByUserId(Long userId);
  Optional<WeeklyGoal> findByIdAndUserId(Long id, Long userId);
}
