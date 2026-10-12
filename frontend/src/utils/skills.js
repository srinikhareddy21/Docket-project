/**
 * Deterministic skill match.
 *   score = matched required skills / total required skills * 100
 * Case, whitespace, punctuation and a small alias table are normalized.
 * The backend (Phase 4) must implement the same rules; it is the source of truth.
 */
const ALIASES = {
  js: 'javascript', ecmascript: 'javascript', ts: 'typescript', reactjs: 'react', 'react.js': 'react',
  nodejs: 'node', 'node.js': 'node', py: 'python', postgres: 'postgresql', mongo: 'mongodb',
  k8s: 'kubernetes', cpp: 'c++', 'c plus plus': 'c++', springboot: 'spring boot', 'spring-boot': 'spring boot',
  ds: 'data structures', dsa: 'data structures and algorithms', oop: 'object oriented programming', oops: 'object oriented programming',
  ml: 'machine learning', html5: 'html', css3: 'css', 'rest api': 'rest', 'restful apis': 'rest',
};

export function normalizeSkill(s) {
  const k = String(s || '').toLowerCase().trim().replace(/\s+/g, ' ');
  return ALIASES[k] || k;
}

export function splitSkills(text) {
  return String(text || '').split(/[,\n;]/).map((s) => s.trim()).filter(Boolean);
}

export function matchSkills(userSkills = [], requiredSkills = []) {
  const have = new Set(userSkills.map(normalizeSkill));
  const seen = new Set();
  const required = requiredSkills.filter((s) => { const n = normalizeSkill(s); if (!n || seen.has(n)) return false; seen.add(n); return true; });
  if (required.length === 0) return { score: null, matched: [], missing: [], total: 0 };
  const matched = required.filter((s) => have.has(normalizeSkill(s)));
  const missing = required.filter((s) => !have.has(normalizeSkill(s)));
  return { score: (matched.length / required.length) * 100, matched, missing, total: required.length };
}
