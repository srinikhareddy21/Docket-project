// Proves the frontend's priority and skill-match functions give the SAME answers as the backend classes
// (the backend is the source of truth). Needs a JDK (javac + java) on PATH; skipped with a notice otherwise.
// Run: node --test scripts/parity.test.mjs   (also part of: sh scripts/check-all.sh)
import test from 'node:test';
import assert from 'node:assert/strict';
import { execFileSync, spawnSync } from 'node:child_process';
import { mkdtempSync, readFileSync } from 'node:fs';
import { tmpdir } from 'node:os';
import path from 'node:path';
import { fileURLToPath } from 'node:url';
import { computePriority } from '../frontend/src/utils/priority.js';
import { matchSkills, normalizeSkill } from '../frontend/src/utils/skills.js';

const root = path.resolve(path.dirname(fileURLToPath(import.meta.url)), '..');
const hasJdk = spawnSync('javac', ['-version']).status === 0;
const opts = { skip: hasJdk ? false : 'javac not found on PATH' };

function runJava(lines) {
  const out = mkdtempSync(path.join(tmpdir(), 'parity-'));
  const be = path.join(root, 'backend/src/main/java/com/docket');
  execFileSync('javac', ['-d', out, path.join(be, 'applications/Priority.java'), path.join(be, 'applications/Vocab.java'), path.join(be, 'skills/SkillMatcher.java'), path.join(root, 'scripts/parity/Parity.java')]);
  return execFileSync('java', ['-cp', out, 'Parity'], { input: lines.join('\n') + '\n', maxBuffer: 1 << 28 }).toString().trimEnd().split('\n');
}

const NOW = Date.parse('2026-10-10T12:00:00Z');
const iso = (ms) => new Date(ms).toISOString();
const H = 3600000, D = 24 * H;

test('priority: frontend == backend on every boundary combination', opts, () => {
  const statuses = ['Applied', 'Shortlisted', 'Interview', 'Selected', 'Rejected'];
  const deadlines = [null, -100 * H, -1 * H, 0.5 * H, 47 * H, 48 * H, 49 * H, 7 * D - H, 7 * D, 7 * D + H, 14 * D, 14 * D + H];
  const interviews = [null, 1 * H, 47 * H, 48 * H, 49 * H, 7 * D, 7 * D + H, 30 * D];
  const followDays = [null, -3, -1, 0, 1];
  const staleDays = [null, 0, 13, 14, 15];
  const cases = [];
  for (const s of statuses) for (const d of deadlines) for (const i of interviews) for (const f of followDays) for (const st of staleDays) for (const prep of [false, true])
    cases.push({ s, d, i, f, st, prep });
  const todayUtc = new Date(NOW).toISOString().slice(0, 10);
  const dayStr = (off) => new Date(Date.parse(todayUtc + 'T00:00:00Z') + off * D).toISOString().slice(0, 10);
  const lines = cases.map((c) => ['P', iso(NOW), c.s, c.d == null ? '~' : iso(NOW + c.d), c.f == null ? '~' : dayStr(c.f), c.st == null ? '~' : iso(NOW - c.st * D), c.i == null ? '~' : iso(NOW + c.i), c.prep ? '1' : '0'].join('|'));
  const java = runJava(lines);
  assert.equal(java.length, cases.length);
  const diffs = [];
  cases.forEach((c, k) => {
    const fe = computePriority({
      application: { currentStatus: c.s, deadline: c.d == null ? null : iso(NOW + c.d) },
      interviews: c.i == null ? [] : [{ interviewDate: iso(NOW + c.i) }],
      checklist: c.prep ? [{ type: 'PREP', completed: false }] : [],
      followUpDate: c.f == null ? null : dayStr(c.f),
      lastStatusChangeAt: c.st == null ? null : iso(NOW - c.st * D),
    }, NOW);
    const [score, level, reasons] = java[k].split('|');
    const got = `${fe.score}|${fe.level}|${[...fe.reasons].sort().join(';')}`;
    const want = `${score}|${level}|${reasons.split(';').filter(Boolean).sort().join(';')}`;
    // "Nothing time-sensitive" is only a UI sentence; the backend returns an empty list for it.
    const norm = (x) => x.replace('Nothing time-sensitive right now.', '');
    if (norm(got) !== norm(want)) diffs.push({ case: c, frontend: got, backend: want });
  });
  assert.equal(diffs.length, 0, `${diffs.length} of ${cases.length} scenarios differ. First 3:\n` + JSON.stringify(diffs.slice(0, 3), null, 1));
});

test('skills: frontend == backend (score, matched, missing) incl. every alias', opts, () => {
  const src = readFileSync(path.join(root, 'frontend/src/utils/skills.js'), 'utf8');
  const block = src.slice(src.indexOf('const ALIASES'), src.indexOf('};', src.indexOf('const ALIASES')));
  const pairs = [...block.matchAll(/'?([\w.+\- ]+)'?\s*:\s*'([^']+)'/g)].map((m) => [m[1], m[2]]);
  assert.ok(pairs.length > 15, 'could not read the alias table');
  const cases = [];
  for (const [k, v] of pairs) { cases.push([[v], [k]]); cases.push([[k], [v]]); cases.push([[v.toUpperCase()], [`  ${k}  `]]); }
  cases.push([['Java', 'SQL'], ['java', 'SQL', 'Python']]);                                   // 2 of 3
  cases.push([['a', 'b', 'c', 'd', 'e', 'f', 'g', 'h'], ['a', 'b', 'c', 'd', 'e', 'f', 'g', 'h', 'x', 'y', 'z']]); // 8 of 11
  cases.push([['React'], ['React', 'react', 'REACT', ' react ']]);                           // de-duplicated -> 1 of 1
  cases.push([['C++'], ['c++', 'C Plus Plus']]);
  cases.push([[], ['Java']]); cases.push([['Java'], []]); cases.push([['Java'], ['', '  ']]);
  const lines = cases.map(([u, r]) => ['S', u.join(',') || '~', r.join(',') || '~'].join('|'));
  const java = runJava(lines);
  const diffs = [];
  cases.forEach(([u, r], k) => {
    const m = matchSkills(u, r);
    const got = `${m.score == null ? 'null' : m.score.toFixed(6)}|${m.matched.map((x) => x).join(',')}|${m.missing.join(',')}`;
    // backend lower-cases/normalises the returned names; compare on normalised names
    const nrm = (line) => { const [s, a, b] = line.split('|'); const f = (x) => x.split(',').filter(Boolean).map(normalizeSkill).join(','); return `${s}|${f(a)}|${f(b)}`; };
    if (nrm(got) !== nrm(java[k])) diffs.push({ user: u, required: r, frontend: got, backend: java[k] });
  });
  assert.equal(diffs.length, 0, `${diffs.length} differ. First 3:\n` + JSON.stringify(diffs.slice(0, 3), null, 1));
});
