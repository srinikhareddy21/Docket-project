package com.docket.calendar;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface GoogleConnectionRepository extends JpaRepository<GoogleConnection, Long> {
  @Query("select c.userId from GoogleConnection c where c.status = 'CONNECTED'")
  List<Long> connectedUserIds();
}
