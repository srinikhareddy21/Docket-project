#!/usr/bin/env node
// Static audit of the DOCKET repo. Run from the repo root:  node scripts/audit.mjs
// Exits 1 on any failure. It reads files only; it does not start anything.
import { readdirSync, readFileSync, statSync, existsSync } from 'node:fs';
import { join, relative, sep } from 'node:path';

const root = process.cwd();
const results = [];
const check = (name, problems) => results.push({ name, problems });

function walk(dir, out = []) {
  if (!existsSync(dir)) return out;
  for (const e of readdirSync(dir)) {
    if (['node_modules', 'target', 'dist', '.git', 'data'].includes(e)) continue;
    const p = join(dir, e);
    statSync(p).isDirectory() ? walk(p, out) : out.push(p);
  }
  return out;
}
const rel = (p) => relative(root, p).split(sep).join('/');
const read = (p) => readFileSync(p, 'utf8');
const filesIn = (dir, exts) => walk(join(root, dir)).filter((f) => exts.some((x) => f.endsWith(x)));
const grep = (files, re, skip = () => false) => files.flatMap((f) => read(f).split('\n')
  .map((line, i) => ({ f: rel(f), n: i + 1, line })).filter(({ line, f }) => re.test(line) && !skip(f, line))
  .map(({ f, n, line }) => `${f}:${n}: ${line.trim().slice(0, 140)}`));

// 1. Migrations create schema only: no seed rows, ever.
const migrations = filesIn('backend/src/main/resources/db/migration', ['.sql']);
check('migrations exist and are sequentially numbered', (() => {
  const nums = migrations.map((f) => Number(/V(\d+)__/.exec(f)?.[1])).sort((a, b) => a - b);
  const bad = nums.some((n, i) => n !== i + 1);
  return nums.length === 0 || bad ? [`found versions: ${nums.join(', ') || 'none'}`] : [];
})());
check('no INSERT / REPLACE / LOAD DATA in any migration (no seed data)',
  grep(migrations, /^\s*(INSERT\s+INTO|REPLACE\s+INTO|LOAD\s+DATA)/i));
check('no data.sql / schema.sql / import.sql on the classpath',
  walk(join(root, 'backend/src')).filter((f) => /(^|\/)(data|schema|import)[^/]*\.sql$/.test(rel(f)) && !rel(f).includes('/db/migration/')).map(rel));

// 2. No startup runners that could create users or records.
const java = filesIn('backend/src/main/java', ['.java']);
check('no CommandLineRunner / ApplicationRunner / ApplicationReadyEvent seeders',
  grep(java, /\b(CommandLineRunner|ApplicationRunner|ApplicationReadyEvent|InitializingBean)\b/));

// 3. Frontend and extension contain no fabricated data or demo mode.
const clientFiles = [...filesIn('frontend/src', ['.js', '.jsx']), ...filesIn('extension', ['.js', '.html'])]
  .filter((f) => !rel(f).startsWith('frontend/src/test/') && !rel(f).startsWith('extension/test/'));
check('no demo / sample-data / lorem / faker / random-data code in client code',
  grep(clientFiles, /(load\s+demo|demo\s+(data|mode|account)|sample\s+(data|applications?)|lorem ipsum|faker|Math\.random\()/i));
check('no default credentials in code or env examples',
  grep([...java, ...clientFiles, ...filesIn('backend', ['.example']), ...filesIn('deploy', ['.example'])],
    /(admin@|test@example|demo@|password\s*[:=]\s*["'](password|admin|123456|changeme)["'])/i));

// 4. Browser storage: drafts only (never tokens).
check('localStorage/sessionStorage used only by the draft helper',
  grep(clientFiles, /\b(localStorage|sessionStorage)\b/, (f) => f === 'frontend/src/utils/drafts.js' || f === 'extension/lib.js' || f === 'extension/options.js' || f === 'extension/popup.js'));
check('frontend never stores or reads tokens in web storage',
  grep(filesIn('frontend/src', ['.js', '.jsx']).filter((f) => !rel(f).includes('/test/')), /(localStorage|sessionStorage)[^;\n]*(token|jwt|password)/i));

// 5. No secrets in client code or committed env files.
check('no secrets or key material in client code',
  grep(clientFiles, /(JWT_SECRET|client_secret|GOOGLE_CLIENT_SECRET|TOKEN_ENCRYPTION_KEY|-----BEGIN [A-Z ]*PRIVATE KEY|AIza[0-9A-Za-z_-]{30,})/));
check('real .env files are not in the tree',
  walk(root).filter((f) => /(^|\/)\.env(\.production)?$/.test(rel(f))).map(rel));
check('.env.example files leave secrets blank',
  [...filesIn('backend', ['.env.example']), ...filesIn('deploy', ['.env.example'])].flatMap((f) =>
    read(f).split('\n').filter((l) => /^(JWT_SECRET|GOOGLE_CLIENT_SECRET|TOKEN_ENCRYPTION_KEY)=.+/.test(l)).map((l) => `${rel(f)}: ${l.split('=')[0]} has a value`)));
check('.gitignore keeps .env and uploads out of git',
  ['backend/.gitignore'].flatMap((f) => existsSync(join(root, f)) ? ['.env', 'data/'].filter((x) => !read(join(root, f)).includes(x)).map((x) => `${f} missing ${x}`) : [`${f} missing`]));

// 6. Logging hygiene: no secrets or full URLs/addresses in log calls.
// Message text may say "Password reset"; what matters is a variable with that name passed as a log argument.
const logArgs = (line) => line.replace(/"(?:[^"\\]|\\.)*"/g, '""');
check('no log statement passes a password, token, secret or raw URL as an argument',
  grep(java, /log\.(trace|debug|info|warn|error)\(/, (f, line) => !/\b(password|passwd|secret|refreshToken|accessToken|rawToken|token|url|uri)\b/i.test(logArgs(line))));
check('exception handler never returns stack traces',
  grep(filesIn('backend/src/main/java/com/docket/web', ['.java']), /printStackTrace|getStackTrace/));

// 7. Ownership: unscoped findById is allowed only on tables keyed by the caller's own user id.
const ownedByUserId = /(prefsRepo|users|conns)\.findById\(/;   // keyed by user_id itself, or token-derived id
check('no unscoped findById / getReferenceById on user-owned records',
  grep(java, /\.(findById|getReferenceById|getById|deleteById)\(/, (f, line) => ownedByUserId.test(line) || /users\.deleteById\(userId\)/.test(line)));
check('every private controller takes the authenticated principal',
  filesIn('backend/src/main/java/com/docket', ['Controller.java'])
    .filter((f) => !/(AuthController|ShareController|CalendarController|ExtensionController|HealthController)/.test(f))
    .filter((f) => !/AuthUser/.test(read(f))).map(rel));

// 8. Accessibility and motion basics.
const css = read(join(root, 'frontend/src/styles/index.css'));
check('prefers-reduced-motion is honored in CSS and Motion',
  [/prefers-reduced-motion: reduce/.test(css) ? null : 'CSS media query missing',
   /reducedMotion="user"/.test(read(join(root, 'frontend/src/main.jsx'))) ? null : 'MotionConfig reducedMotion="user" missing',
   /:focus-visible/.test(css) ? null : 'focus-visible style missing',
   /Skip to main content/.test(read(join(root, 'frontend/src/layouts/AppLayout.jsx'))) ? null : 'skip link missing'].filter(Boolean));
check('colors live in tokens.css only (no hex colors in components)',
  grep(filesIn('frontend/src', ['.jsx']), /#[0-9a-fA-F]{6}\b|#[0-9a-fA-F]{3}\b(?![0-9a-zA-Z-])/, (f, line) => /href=["']#|to=["']#|id=["']#/.test(line) || /\bhref=/.test(line)));

// ---- report ----
let failed = 0;
for (const r of results) {
  const ok = r.problems.length === 0;
  if (!ok) failed++;
  console.log(`${ok ? 'PASS' : 'FAIL'}  ${r.name}`);
  for (const p of r.problems.slice(0, 15)) console.log(`        ${p}`);
}
console.log(`\n${results.length - failed}/${results.length} audit checks passed`);
process.exit(failed ? 1 : 0);
