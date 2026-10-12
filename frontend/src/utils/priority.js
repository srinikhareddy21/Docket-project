/**
 * Transparent priority score (0-100). The backend implements the same formula.
 *
 * Points (capped, then summed):
 *   Deadline        overdue/expired 50 | <=48h 55 | <=7d 30 | <=14d 15        (skipped for closed statuses)
 *   Interview       upcoming <=48h 50 | <=7d 30 | later 10
 *   Prep gap        incomplete prep and interview <=7d  +15
 *   Follow-up       overdue 35 | due today 25
 *   Staleness       no status change in >14 days while Applied/Shortlisted  +5 (max, never dominates)
 * Levels: High >= 50, Medium 25-49, Low < 25.
 * Closed applications (Selected, Rejected, Withdrawn) always score 0.
 * A single interview or deadline within ~24–48 hours is High on its own.
 */
const CLOSED = ['Selected', 'Rejected', 'Withdrawn'];
const day = 86400000;

export function computePriority({ application, interviews = [], checklist = [], followUpDate, lastStatusChangeAt }, now = Date.now()) {
  if (!application || CLOSED.includes(application.currentStatus)) return { score: 0, level: 'Low', reasons: ['Application is closed.'] };
  let score = 0; const reasons = [];

  if (application.deadline) {
    const diff = new Date(application.deadline).getTime() - now;
    if (diff < 0) { score += 50; reasons.push('Deadline has passed.'); }
    else if (diff <= 2 * day) { score += 55; reasons.push('Deadline within 48 hours.'); }
    else if (diff <= 7 * day) { score += 30; reasons.push('Deadline within 7 days.'); }
    else if (diff <= 14 * day) { score += 15; reasons.push('Deadline within 14 days.'); }
  }

  const upcoming = interviews.filter((i) => new Date(i.interviewDate).getTime() >= now).sort((a, b) => new Date(a.interviewDate) - new Date(b.interviewDate))[0];
  if (upcoming) {
    const diff = new Date(upcoming.interviewDate).getTime() - now;
    if (diff <= 2 * day) { score += 50; reasons.push('Interview within 48 hours.'); }
    else if (diff <= 7 * day) { score += 30; reasons.push('Interview within 7 days.'); }
    else { score += 10; reasons.push('Interview scheduled.'); }
    const prep = checklist.filter((c) => c.type === 'PREP');
    if (diff <= 7 * day && prep.some((c) => !c.completed)) { score += 15; reasons.push('Interview preparation is incomplete.'); }
  }

  if (followUpDate) {
    // Follow-up dates are whole days compared with "today" in UTC: the same rule as backend Priority.java (a parity test enforces it).
    const fd = Date.parse(String(followUpDate).slice(0, 10) + 'T00:00:00Z');
    const todayUtc = Math.floor(now / day) * day;
    if (fd < todayUtc) { score += 35; reasons.push('Follow-up is overdue.'); }
    else if (fd === todayUtc) { score += 25; reasons.push('Follow-up due today.'); }
  }

  if (lastStatusChangeAt && ['Applied', 'Shortlisted'].includes(application.currentStatus) && now - new Date(lastStatusChangeAt).getTime() > 14 * day) {
    score += 5; reasons.push('No update in over 14 days.');
  }

  score = Math.min(100, score);
  const level = score >= 50 ? 'High' : score >= 25 ? 'Medium' : 'Low';
  if (!reasons.length) reasons.push('Nothing time-sensitive right now.');
  return { score, level, reasons };
}
