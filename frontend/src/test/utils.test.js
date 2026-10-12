import { describe, it, expect } from 'vitest';
import { computePriority } from '../utils/priority';
import { matchSkills, normalizeSkill, splitSkills } from '../utils/skills';
import { ratio, pct, daysUntil, startOfWeek } from '../utils/format';
import { loadDraft, saveDraft, clearDraft, clearAllDrafts } from '../utils/drafts';

const NOW = Date.parse('2026-03-10T12:00:00Z');
const h = (n) => new Date(NOW + n * 3600000).toISOString();
const open = { currentStatus: 'Applied' };

describe('skill match', () => {
  it('8 of 11 required skills = 72.7%', () => {
    const req = Array.from({ length: 11 }, (_, i) => `skill${i}`);
    const have = req.slice(0, 8);
    const r = matchSkills(have, req);
    expect(r.matched).toHaveLength(8);
    expect(r.missing).toHaveLength(3);
    expect(r.score).toBeCloseTo(72.727, 2);
  });
  it('returns null (not 0) when there are no required skills', () => {
    expect(matchSkills(['js'], []).score).toBeNull();
    expect(matchSkills(['js'], ['  ', '']).score).toBeNull();
  });
  it('normalizes case, spacing and aliases', () => {
    expect(normalizeSkill('  JS ')).toBe('javascript');
    expect(normalizeSkill('Node.JS')).toBe('node');
    expect(matchSkills(['JavaScript'], ['js', 'JS', ' javascript ']).total).toBe(1);
  });
  it('splits on commas, semicolons and newlines', () => {
    expect(splitSkills('a, b;c\nd,, ')).toEqual(['a', 'b', 'c', 'd']);
  });
  it('a new user with no skills matches nothing', () => {
    expect(matchSkills([], ['react']).score).toBe(0);
  });
});

describe('priority score', () => {
  it('closed applications always score 0', () => {
    for (const s of ['Selected', 'Rejected', 'Withdrawn'])
      expect(computePriority({ application: { currentStatus: s, deadline: h(1) } }, NOW).score).toBe(0);
  });
  it('deadline tiers: passed 50, 48h 55, 7d 30, 14d 15, later 0', () => {
    const s = (d) => computePriority({ application: { ...open, deadline: d } }, NOW).score;
    expect(s(h(-1))).toBe(50);
    expect(s(h(47))).toBe(55);
    expect(s(h(24 * 6))).toBe(30);
    expect(s(h(24 * 13))).toBe(15);
    expect(s(h(24 * 30))).toBe(0);
  });
  it('an old application alone never reaches Medium', () => {
    const r = computePriority({ application: open, lastStatusChangeAt: h(-24 * 365) }, NOW);
    expect(r.score).toBe(5);
    expect(r.level).toBe('Low');
  });
  it('interview tomorrow is High on its own', () => {
    const r = computePriority({ application: open, interviews: [{ interviewDate: h(24) }] }, NOW);
    expect(r.score).toBe(50);
    expect(r.level).toBe('High');
  });
  it('deadline tomorrow is High on its own', () => {
    const r = computePriority({ application: { ...open, deadline: h(24) } }, NOW);
    expect(r.score).toBe(55);
    expect(r.level).toBe('High');
  });
  it('overdue follow-up + imminent deadline is High with reasons', () => {
    const r = computePriority({ application: { ...open, deadline: h(10) }, followUpDate: h(-48) }, NOW);
    expect(r.level).toBe('High');
    expect(r.reasons).toEqual(expect.arrayContaining(['Deadline within 48 hours.', 'Follow-up is overdue.']));
  });
  it('follow-up dates are whole days in UTC, like the backend: today 25, yesterday 35, tomorrow 0', () => {
    const noon = Date.parse('2026-10-10T12:00:00Z');
    const f = (d) => computePriority({ application: open, followUpDate: d }, noon);
    expect(f('2026-10-10').score).toBe(25);
    expect(f('2026-10-10').reasons).toContain('Follow-up due today.');
    expect(f('2026-10-09').score).toBe(35);
    expect(f('2026-10-11').score).toBe(0);
  });
  it('incomplete prep adds points only when the interview is within 7 days', () => {
    const prep = [{ type: 'PREP', completed: false }];
    const near = computePriority({ application: open, interviews: [{ interviewDate: h(24 * 3) }], checklist: prep }, NOW);
    const far = computePriority({ application: open, interviews: [{ interviewDate: h(24 * 20) }], checklist: prep }, NOW);
    expect(near.score).toBe(30 + 15);
    expect(far.score).toBe(10);
  });
  it('caps at 100 and explains a quiet application', () => {
    expect(computePriority({ application: open }, NOW).reasons).toEqual(['Nothing time-sensitive right now.']);
  });
});

describe('ratios and dates', () => {
  it('ratio never divides by zero', () => {
    expect(ratio(1, 0)).toBeNull();
    expect(ratio(1, 4)).toBe(25);
  });
  it('pct rounds to one decimal', () => expect(pct(72.7272)).toBe('72.7%'));
  it('daysUntil handles empty and past dates', () => {
    expect(daysUntil(null)).toBeNull();
    expect(daysUntil(new Date(Date.now() - 3 * 86400000).toISOString())).toBeLessThan(0);
  });
  it('week starts on Monday', () => {
    const sun = new Date(2026, 2, 8, 15, 0); // Sunday 8 Mar 2026
    expect(startOfWeek(sun).getDay()).toBe(1);
    expect(startOfWeek(sun).getDate()).toBe(2);
  });
});

describe('draft storage', () => {
  it('saves, loads and clears a draft; unknown key is null', () => {
    expect(loadDraft('x')).toBeNull();
    saveDraft('x', { a: 1 });
    expect(loadDraft('x')).toEqual({ a: 1 });
    clearDraft('x');
    expect(loadDraft('x')).toBeNull();
  });
  it('clearAllDrafts removes only docket drafts (used on logout)', () => {
    saveDraft('a', 1); saveDraft('b', 2); localStorage.setItem('other', 'keep');
    clearAllDrafts();
    expect(loadDraft('a')).toBeNull();
    expect(localStorage.getItem('other')).toBe('keep');
  });
  it('survives corrupted JSON', () => {
    localStorage.setItem('docket.draft.bad', '{not json');
    expect(loadDraft('bad')).toBeNull();
  });
});
