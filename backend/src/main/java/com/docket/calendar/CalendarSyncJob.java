package com.docket.calendar;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/** Every 30 minutes: syncs each connected user. Assumes a single running backend instance. */
@Component
public class CalendarSyncJob {
  private final CalendarService service;
  public CalendarSyncJob(CalendarService service) { this.service = service; }

  @Scheduled(fixedDelayString = "PT30M", initialDelayString = "PT2M")
  public void run() { for (long id : service.connectedUsers()) service.syncQuietly(id); }
}
