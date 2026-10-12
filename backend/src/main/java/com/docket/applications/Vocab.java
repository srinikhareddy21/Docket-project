package com.docket.applications;

import java.util.Set;

/** Allowed values. The frontend's config/constants.js mirrors these exactly. */
public final class Vocab {
  private Vocab() {}

  public static final String STATUS_REGEX = "Applied|Shortlisted|Assessment|Interview|Selected|Rejected|Withdrawn";
  public static final String JOB_TYPE_REGEX = "Internship|Full-time|Part-time|Other";
  public static final String WORK_MODE_REGEX = "Remote|Hybrid|Onsite";
  public static final String SOURCE_REGEX = "Referral|LinkedIn|Other job portal|Company career page|College placement cell|Other";

  public static final String DEFAULT_STATUS = "Applied";
  /** Applications in these statuses are finished: no deadline, follow-up or priority pressure. */
  public static final Set<String> CLOSED = Set.of("Selected", "Rejected", "Withdrawn");
  public static final Set<String> WAITING = Set.of("Applied", "Shortlisted");

  public static boolean isClosed(String status) { return CLOSED.contains(status); }
}
