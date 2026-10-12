package com.docket.applications;

import com.docket.applications.ApplicationDtos.*;
import com.docket.checklist.ChecklistRepository;
import com.docket.config.AppProperties;
import com.docket.interviews.InterviewRepository;
import com.docket.reminders.ReminderService;
import com.docket.notifications.NotificationService;
import com.docket.calendar.CalendarService;
import com.docket.skills.SkillMatcher;
import com.docket.skills.SkillRepository;
import com.docket.web.ApiException;
import java.net.URI;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ApplicationService {
  private static final int MAX_PAGE_SIZE = 500;
  private static final String NOT_FOUND = "We could not find that application.";
  private static final Set<String> SORT_FIELDS = Set.of("applicationDate", "deadline", "companyName", "createdAt");

  private final ApplicationRepository apps;
  private final StatusHistoryRepository history;
  private final AppProperties props;
  private final InterviewRepository interviews;
  private final ChecklistRepository checklist;
  private final SkillRepository skills;
  private final ReminderService reminders;
  private final NotificationService notifications;
  private final CalendarService calendar;

  public ApplicationService(ApplicationRepository apps, StatusHistoryRepository history, AppProperties props,
                            InterviewRepository interviews, ChecklistRepository checklist, SkillRepository skills,
                            ReminderService reminders, NotificationService notifications, CalendarService calendar) {
    this.apps = apps; this.history = history; this.props = props;
    this.interviews = interviews; this.checklist = checklist; this.skills = skills;
    this.reminders = reminders; this.notifications = notifications; this.calendar = calendar;
  }

  /** Everything needed to score a user's applications, loaded once per request (not once per row). */
  private record Ctx(Instant now, LocalDate today, Map<Long, Instant> nextInterview, Set<Long> prepIncomplete, List<String> userSkills) {}

  private Ctx ctx(long userId) {
    Instant now = Instant.now();
    Map<Long, Instant> next = new java.util.HashMap<>();
    for (Object[] r : interviews.nextInterviews(userId, now)) next.put((Long) r[0], (Instant) r[1]);
    return new Ctx(now, LocalDate.now(ZoneOffset.UTC), next, checklist.applicationsWithIncompletePrep(userId), skills.names(userId));
  }

  // ---------- reads ----------

  public record Query(String q, String status, String jobType, String source, String workMode, String location,
                      String sort, int page, int size) {}

  @Transactional(readOnly = true)
  public PagedResponse<ApplicationResponse> list(long userId, Query in) {
    int size = Math.min(Math.max(in.size(), 1), MAX_PAGE_SIZE);
    int page = Math.max(in.page(), 0);
    String[] sortParts = (in.sort() == null ? "" : in.sort()).split(",");
    String field = sortParts.length > 0 ? sortParts[0].trim() : "";
    boolean desc = sortParts.length < 2 || !"asc".equalsIgnoreCase(sortParts[1].trim());
    Ctx c = ctx(userId);

    // The search query treats "" as "no filter"; null would make every `:x = ''` check false and return nothing.
    String status = filter(in.status()), jobType = filter(in.jobType()), source = filter(in.source()), workMode = filter(in.workMode());
    String location = Normalizer.likePattern(in.location()), q = Normalizer.likePattern(in.q());

    if ("priority".equals(field)) {
      // Priority is computed from several fields, so it cannot be a database ORDER BY. A personal docket is
      // small, so load the filtered set, rank it, then slice the requested page.
      List<Application> all = apps.search(userId, status, jobType, source, workMode, location, q,
          Pageable.unpaged(Sort.by(Sort.Direction.DESC, "id"))).getContent();
      List<ApplicationResponse> ranked = new ArrayList<>(all.stream().map(a -> toResponse(a, c, false)).toList());
      Comparator<ApplicationResponse> byScore = Comparator.comparingInt(r -> r.priority().score());
      ranked.sort((desc ? byScore.reversed() : byScore).thenComparing(ApplicationResponse::id, Comparator.reverseOrder()));
      int from = (int) Math.min((long) page * size, ranked.size());
      int to = Math.min(from + size, ranked.size());
      int pages = (int) Math.ceil(ranked.size() / (double) size);
      return new PagedResponse<>(ranked.subList(from, to), ranked.size(), pages, page, size);
    }

    Sort sort = SORT_FIELDS.contains(field)
        ? Sort.by(new Sort.Order(desc ? Sort.Direction.DESC : Sort.Direction.ASC, field).with(Sort.NullHandling.NULLS_LAST), Sort.Order.desc("id"))
        : Sort.by(new Sort.Order(Sort.Direction.DESC, "applicationDate").with(Sort.NullHandling.NULLS_LAST), Sort.Order.desc("id"));
    Page<Application> result = apps.search(userId, status, jobType, source, workMode, location, q, PageRequest.of(page, size, sort));
    return new PagedResponse<>(result.getContent().stream().map(a -> toResponse(a, c, false)).toList(),
        result.getTotalElements(), result.getTotalPages(), page, size);
  }

  @Transactional(readOnly = true)
  public ApplicationResponse get(long userId, long id) {
    return toResponse(find(userId, id), ctx(userId), true);
  }

  @Transactional(readOnly = true)
  public List<HistoryEntry> history(long userId, long id) {
    find(userId, id);   // 404 unless it exists and belongs to this user
    return history.findByApplicationIdAndUserIdOrderByChangedAtDescIdDesc(id, userId).stream()
        .map(h -> new HistoryEntry(h.id, h.previousStatus, h.newStatus, h.changedAt, h.note)).toList();
  }

  @Transactional(readOnly = true)
  public List<DuplicateMatch> duplicates(long userId, String company, String position, String url, Long excludeId) {
    String linkKey = Normalizer.urlKey(url);
    String companyKey = Normalizer.text(company), positionKey = Normalizer.text(position);
    if (linkKey.isEmpty() && (companyKey.isEmpty() || positionKey.isEmpty())) return List.of();
    return apps.findDuplicates(userId, linkKey, companyKey, positionKey, excludeId == null ? 0L : excludeId, PageRequest.of(0, 5))
        .stream().map(a -> new DuplicateMatch(a.id, a.companyName, a.position, a.currentStatus)).toList();
  }

  @Transactional(readOnly = true)
  public Suggestions suggestions(long userId) {
    return new Suggestions(apps.recentCompanies(userId, PageRequest.of(0, 30)), apps.recentRoles(userId, PageRequest.of(0, 30)));
  }

  // ---------- writes ----------

  @Transactional
  public ApplicationResponse create(long userId, ApplicationRequest req) {
    Application a = new Application(userId);
    apply(a, req);
    String status = req.currentStatus() == null ? Vocab.DEFAULT_STATUS : req.currentStatus();
    a.currentStatus = status;
    a.statusChangedAt = Instant.now();
    a = apps.save(a);
    history.save(new StatusHistory(a.id, userId, null, status, null, a.statusChangedAt));
    reminders.syncQuietly(userId); notifications.syncQuietly(userId); calendar.syncQuietly(userId);
    return toResponse(a, ctx(userId), true);
  }

  @Transactional
  public ApplicationResponse update(long userId, long id, ApplicationRequest req) {
    Application a = find(userId, id);
    apply(a, req);
    if (req.currentStatus() != null) changeStatus(a, req.currentStatus(), null);
    a = apps.save(a);
    reminders.syncQuietly(userId); notifications.syncQuietly(userId); calendar.syncQuietly(userId);
    return toResponse(a, ctx(userId), true);
  }

  @Transactional
  public ApplicationResponse setStatus(long userId, long id, StatusRequest req) {
    Application a = find(userId, id);
    changeStatus(a, req.status(), blank(req.note()));
    a = apps.save(a);
    reminders.syncQuietly(userId); notifications.syncQuietly(userId); calendar.syncQuietly(userId);
    return toResponse(a, ctx(userId), true);
  }

  @Transactional
  public BulkResult bulkStatus(long userId, BulkStatusRequest req) {
    Set<Long> ids = new LinkedHashSet<>(req.ids());
    List<Application> found = apps.findByIdInAndUserIdAndDeletedAtIsNull(ids, userId);
    // All or nothing: if any id is missing or belongs to someone else, change nothing and reveal nothing.
    if (found.size() != ids.size()) throw ApiException.notFound("One or more selected applications could not be found.");
    int changed = 0;
    for (Application a : found) if (changeStatus(a, req.status(), blank(req.note()))) { apps.save(a); changed++; }
    if (changed > 0) reminders.syncQuietly(userId); notifications.syncQuietly(userId); calendar.syncQuietly(userId);
    return new BulkResult(changed);
  }

  @Transactional
  public void delete(long userId, long id) {
    Application a = find(userId, id);
    a.deletedAt = Instant.now();
    apps.save(a);
    reminders.syncQuietly(userId); notifications.syncQuietly(userId); calendar.syncQuietly(userId);
  }

  @Transactional
  public ApplicationResponse restore(long userId, long id) {
    Application a = apps.findByIdAndUserId(id, userId).orElseThrow(() -> ApiException.notFound(NOT_FOUND));
    if (a.deletedAt != null) {
      if (a.deletedAt.plus(props.applications().undoWindow()).isBefore(Instant.now()))
        throw ApiException.conflict("The undo window has passed, so this application can no longer be restored.", null);
      a.deletedAt = null;
      a = apps.save(a);
    }
    return toResponse(a, ctx(userId), true);
  }

  @Transactional(readOnly = true)
  public byte[] exportCsv(long userId) {
    List<String> mine = skills.names(userId);
    Ctx c = ctx(userId);
    StringBuilder out = new StringBuilder(Csv.row(List.of(
        "Company", "Job title", "Status", "Priority", "Priority score", "Job type", "Work mode", "Location",
        "Application date", "Deadline", "Source", "Application link", "Follow-up date", "Match score", "Next action")));
    for (Application a : apps.findByUserIdAndDeletedAtIsNullOrderByApplicationDateDescIdDesc(userId)) {
      ApplicationResponse r = toResponse(a, c, false);
      out.append(Csv.row(java.util.Arrays.asList(
          a.companyName, a.position, a.currentStatus,
          r.priority() == null ? null : r.priority().level(),
          r.priority() == null ? null : String.valueOf(r.priority().score()),
          a.jobType, a.workMode, a.location,
          a.applicationDate == null ? null : a.applicationDate.toString(),
          a.deadline == null ? null : a.deadline.toString(),
          a.applicationSource, a.applicationLink,
          a.followUpDate == null ? null : a.followUpDate.toString(),
          matchText(SkillMatcher.score(mine, a.requiredSkills)),
          r.nextAction())));
    }
    return Csv.bytes(out.toString());
  }

  private static String matchText(Double score) { return score == null ? null : String.format(Locale.ROOT, "%.1f", score); }

  /** Housekeeping: permanently removes applications deleted longer ago than the retention period. */
  @Transactional
  public int purgeExpired() {
    return purgeDeletedBefore(purgeCutoff());
  }

  /** Applications deleted before this moment are past the retention period. */
  public Instant purgeCutoff() { return Instant.now().minus(props.applications().deletedRetention()); }

  /** Permanently removes applications deleted before the cutoff (dependent rows cascade). Returns how many. */
  @Transactional
  public int purgeDeletedBefore(Instant cutoff) { return apps.purgeDeletedBefore(cutoff); }

  // ---------- internals ----------

  private Application find(long userId, long id) {
    return apps.findByIdAndUserIdAndDeletedAtIsNull(id, userId).orElseThrow(() -> ApiException.notFound(NOT_FOUND));
  }

  /** The one place a status changes, whether it came from the list, Kanban, detail page, bulk update or an edit. */
  private boolean changeStatus(Application a, String next, String note) {
    if (next.equals(a.currentStatus)) return false;
    Instant now = Instant.now();
    history.save(new StatusHistory(a.id, a.userId, a.currentStatus, next, note, now));
    a.currentStatus = next;
    a.statusChangedAt = now;
    return true;
  }

  /** Copies every editable field except status, which goes through changeStatus. */
  private void apply(Application a, ApplicationRequest r) {
    Map<String, String> bad = new LinkedHashMap<>();
    String link = blank(r.applicationLink());
    if (link != null && !validHttpUrl(link)) bad.put("applicationLink", "Enter a full link starting with http:// or https://");
    if (!bad.isEmpty()) throw ApiException.badRequest("Some details are invalid. Check the form and try again.", bad);

    a.companyName = r.companyName().trim();
    a.position = r.position().trim();
    a.jobType = blank(r.jobType());
    a.workMode = blank(r.workMode());
    a.location = blank(r.location());
    a.jobDescription = blank(r.jobDescription());
    a.applicationSource = blank(r.applicationSource());
    a.applicationDate = r.applicationDate();
    a.deadline = r.deadline();
    a.applicationLink = link;
    a.salaryOrStipend = blank(r.salaryOrStipend());
    a.recruiterDetails = blank(r.recruiterDetails());
    a.notes = blank(r.notes());
    a.resumeVersion = blank(r.resumeVersion());
    a.followUpDate = r.followUpDate();
    a.companyKey = Normalizer.text(a.companyName);
    a.positionKey = Normalizer.text(a.position);
    a.linkKey = Normalizer.urlKey(link);

    // Skills: trimmed, de-duplicated ignoring case, original order kept.
    List<String> skills = new ArrayList<>();
    Set<String> seen = new LinkedHashSet<>();
    if (r.requiredSkills() != null) {
      for (String s : r.requiredSkills()) {
        String t = s == null ? "" : s.trim();
        if (!t.isEmpty() && seen.add(t.toLowerCase(Locale.ROOT))) skills.add(t);
      }
    }
    a.requiredSkills.clear();
    a.requiredSkills.addAll(skills);
  }

  private static boolean validHttpUrl(String s) {
    try {
      URI u = new URI(s);
      return u.getScheme() != null && (u.getScheme().equalsIgnoreCase("http") || u.getScheme().equalsIgnoreCase("https"))
          && u.getHost() != null && !u.getHost().isBlank();
    } catch (Exception e) { return false; }
  }

  private static String blank(String s) { return com.docket.web.Strings.blank(s); }
  /** Optional list filter: trimmed value, or "" when absent (never null). */
  public static String filter(String s) { String b = blank(s); return b == null ? "" : b; }

  /** includeLong is false for list rows, which leave out the long free-text fields to keep pages small. */
  ApplicationResponse toResponse(Application a, Ctx c, boolean includeLong) {
    Priority.Result p = Priority.compute(a.currentStatus, a.deadline, a.followUpDate, a.statusChangedAt,
        c.nextInterview().get(a.id), c.prepIncomplete().contains(a.id), c.now(), c.today());
    return new ApplicationResponse(a.id, a.companyName, a.position, a.jobType, a.workMode, a.location,
        includeLong ? a.jobDescription : null, List.copyOf(a.requiredSkills), a.applicationSource, a.applicationDate,
        a.deadline, a.applicationLink, a.salaryOrStipend, a.recruiterDetails, includeLong ? a.notes : null,
        a.currentStatus, a.resumeVersion, a.followUpDate, SkillMatcher.score(c.userSkills(), a.requiredSkills),
        new PriorityDto(p.score(), p.level(), p.reasons()), p.nextAction(), a.createdAt, a.updatedAt);
  }
}
