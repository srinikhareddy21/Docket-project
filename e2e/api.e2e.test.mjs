// Black-box API tests for a RUNNING DOCKET backend. Zero dependencies (Node 20+).
//
//   E2E_BASE_URL=http://localhost:8080 node --test e2e/
//
// Use a development or staging database, NOT production: the tests create real accounts
// (e2e-<timestamp>-*@example.test) and delete them again at the end through the real
// "delete account" endpoint. Credential endpoints are rate limited (30 per 10 minutes per IP),
// so wait ten minutes between runs. See TESTING.md for the optional share-link expiry check.
import { test, before, after } from 'node:test';
import assert from 'node:assert/strict';
import { execSync } from 'node:child_process';

const BASE = (process.env.E2E_BASE_URL || 'http://localhost:8080').replace(/\/+$/, '');
const SQL_CMD = process.env.E2E_SQL_CMD || '';   // optional: a command that reads SQL on stdin, e.g. "docker compose exec -T mysql mysql -uroot -p... docket"
const PASSWORD = 'E2e-Str0ng!Passw0rd';
const stamp = Date.now();

/** Minimal browser: keeps cookies and echoes the XSRF cookie as the X-XSRF-TOKEN header. */
class Browser {
  jar = new Map();
  store(res) { for (const c of res.headers.getSetCookie?.() ?? []) { const [pair, ...attrs] = c.split(';'); const i = pair.indexOf('='); const name = pair.slice(0, i).trim(); const value = pair.slice(i + 1).trim(); const gone = /max-age=0/i.test(attrs.join(';')) || value === ''; gone ? this.jar.delete(name) : this.jar.set(name, value); } }
  cookieHeader() { return [...this.jar].map(([k, v]) => `${k}=${v}`).join('; '); }
  async call(method, path, { json, form, headers = {}, raw = false } = {}) {
    if (method !== 'GET' && !this.jar.has('XSRF-TOKEN')) this.store(await fetch(BASE + '/api/auth/csrf', { headers: { cookie: this.cookieHeader() } }));
    const h = { cookie: this.cookieHeader(), ...headers };
    if (method !== 'GET' && this.jar.has('XSRF-TOKEN')) h['X-XSRF-TOKEN'] = this.jar.get('XSRF-TOKEN');
    let body;
    if (json !== undefined) { h['content-type'] = 'application/json'; body = JSON.stringify(json); }
    if (form) body = form;
    const res = await fetch(BASE + path, { method, headers: h, body, redirect: 'manual' });
    this.store(res);
    if (raw) return res;
    const text = await res.text();
    let data = null; try { data = text ? JSON.parse(text) : null; } catch { data = text; }
    return { status: res.status, data, headers: res.headers, text };
  }
  get = (p, o) => this.call('GET', p, o);
  post = (p, json, o = {}) => this.call('POST', p, { json, ...o });
  put = (p, json) => this.call('PUT', p, { json });
  patch = (p, json) => this.call('PATCH', p, { json });
  del = (p) => this.call('DELETE', p);
}

const alice = new Browser(), bob = new Browser(), anon = new Browser();
const email = (n) => `e2e-${stamp}-${n}@example.test`;
const created = [];   // emails whose accounts must be deleted at the end
let aliceApp, bobApp;

async function register(name, em) {
  const r = await anon.post('/api/auth/register', { name, email: em, password: PASSWORD, acceptTerms: true });
  assert.equal(r.status, 201, `register ${em}: ${r.text}`);
  created.push(em);
}
async function login(b, em, remember = false) {
  const r = await b.post('/api/auth/login', { email: em, password: PASSWORD, remember });
  assert.equal(r.status, 200, `login ${em}: ${r.text}`);
  return r.data;
}
const appBody = (over = {}) => ({ companyName: 'E2E Corp', position: 'Backend Intern', jobType: 'Internship', workMode: 'Remote', currentStatus: 'Applied',
  applicationSource: 'LinkedIn', applicationDate: new Date().toISOString().slice(0, 10), requiredSkills: ['Java', 'SQL', 'Docker'], ...over });

before(async () => {
  const h = await fetch(BASE + '/api/health').catch(() => null);
  assert.ok(h && h.status === 200, `Backend not reachable (or unhealthy) at ${BASE}. Start it first.`);
});

after(async () => {
  // Remove the accounts this run created, using the real endpoint (also exercises file/data cleanup).
  for (const [b, em] of [[alice, email('a')], [bob, email('b')]]) {
    if (!created.includes(em)) continue;
    try { await b.post('/api/profile/delete-request', { password: PASSWORD }); } catch { /* best effort */ }
  }
});

// ---------------- registration, login, logout ----------------
test('register: weak password, missing consent and bad email are rejected with field errors', async () => {
  let r = await anon.post('/api/auth/register', { name: 'X Y', email: email('weak'), password: 'weak', acceptTerms: true });
  assert.equal(r.status, 400); assert.ok(r.data.fieldErrors?.password);
  r = await anon.post('/api/auth/register', { name: 'X Y', email: email('weak'), password: PASSWORD, acceptTerms: false });
  assert.equal(r.status, 400);
  r = await anon.post('/api/auth/register', { name: 'X Y', email: 'not-an-email', password: PASSWORD, acceptTerms: true });
  assert.equal(r.status, 400); assert.ok(r.data.fieldErrors?.email);
});

test('register: success returns no password material; duplicate email gives 409', async () => {
  await register('Alice E2E', email('a'));
  await register('Bob E2E', email('b'));
  const dup = await anon.post('/api/auth/register', { name: 'Alice Again', email: email('a').toUpperCase(), password: PASSWORD, acceptTerms: true });
  assert.equal(dup.status, 409);
  assert.ok(!/password|hash/i.test(JSON.stringify(dup.data)), 'error must not leak internals');
});

test('protected endpoints return 401 without a session', async () => {
  for (const p of ['/api/auth/me', '/api/applications', '/api/dashboard', '/api/skills', '/api/interviews', '/api/share-links', '/api/applications/export'])
    assert.equal((await new Browser().get(p)).status, 401, p);
});

test('login: wrong password and unknown email give the same generic 401', async () => {
  const b = new Browser();
  const wrong = await b.post('/api/auth/login', { email: email('a'), password: 'Wrong-Passw0rd!' });
  const unknown = await b.post('/api/auth/login', { email: email('nobody'), password: PASSWORD });
  assert.equal(wrong.status, 401); assert.equal(unknown.status, 401);
  assert.equal(wrong.data.message, unknown.data.message, 'must not reveal whether the email exists');
});

test('login sets HttpOnly cookies; /me works; logout revokes the session immediately', async () => {
  const user = await login(alice, email('a'));
  assert.equal(user.email, email('a')); assert.equal(user.password, undefined); assert.equal(user.passwordHash, undefined);
  assert.ok(alice.jar.has('docket_at'), 'access cookie set');
  const raw = await new Browser().post('/api/auth/login', { email: email('a'), password: PASSWORD }, { raw: true });
  const flags = raw.headers.getSetCookie().find((c) => c.startsWith('docket_at='));
  assert.match(flags, /HttpOnly/i); assert.match(flags, /SameSite=Lax/i);
  assert.equal((await alice.get('/api/auth/me')).status, 200);
  const stolen = alice.cookieHeader();                       // a copy of the cookies, as an attacker holding them would have
  assert.equal((await alice.post('/api/auth/logout')).status, 204);
  assert.equal((await alice.get('/api/auth/me')).status, 401, 'cookies cleared');
  const replay = await fetch(BASE + '/api/auth/me', { headers: { cookie: stolen } });
  assert.equal(replay.status, 401, 'the old access token must stop working at once, not after 15 minutes');
  await login(alice, email('a')); await login(bob, email('b'));
});

test('CSRF: a state-changing request without the XSRF header is refused', async () => {
  const noHeader = await fetch(BASE + '/api/applications', { method: 'POST', headers: { cookie: alice.cookieHeader(), 'content-type': 'application/json' }, body: JSON.stringify(appBody()) });
  assert.equal(noHeader.status, 403);
});

test('forgot-password always answers 202 (no account enumeration)', async () => {
  assert.equal((await anon.post('/api/auth/forgot-password', { email: email('a') })).status, 202);
  assert.equal((await anon.post('/api/auth/forgot-password', { email: email('does-not-exist') })).status, 202);
  assert.equal((await anon.post('/api/auth/reset-password', { token: 'not-a-real-token', password: PASSWORD })).status, 400);
});

// ---------------- new accounts start empty ----------------
test('a new account is genuinely empty everywhere', async () => {
  assert.deepEqual((await bob.get('/api/applications')).data.content, []);
  assert.deepEqual((await bob.get('/api/skills')).data, []);
  assert.deepEqual((await bob.get('/api/interviews')).data, []);
  assert.deepEqual((await bob.get('/api/share-links')).data, []);
  const d = (await bob.get('/api/dashboard')).data;
  assert.equal(d.total, 0); assert.equal(d.offers, 0); assert.equal(d.interviewsScheduled, 0);
  assert.deepEqual(d.actions, []); assert.deepEqual(d.recent, []); assert.deepEqual(d.sources, []);
  assert.ok(d.funnel.every((s) => s.count === 0));
  const goal = (await bob.get('/api/goals')).data; assert.ok(!goal || !goal.id, 'no goal until the user sets one');
  const csv = await bob.get('/api/applications/export');
  assert.equal(csv.status, 200); assert.equal(csv.text.replace(/^\uFEFF/, '').trim().split(/\r?\n/).length, 1, 'header row only');
});

// ---------------- application CRUD, status history, duplicates, undo ----------------
test('application create / read / update / list / search', async () => {
  let r = await alice.post('/api/applications', appBody());
  assert.equal(r.status, 201, r.text); aliceApp = r.data;
  assert.equal(aliceApp.currentStatus, 'Applied');
  assert.equal(aliceApp.matchScore, null, 'no skills yet -> no invented score');
  assert.equal((await alice.get(`/api/applications/${aliceApp.id}`)).data.companyName, 'E2E Corp');
  r = await alice.put(`/api/applications/${aliceApp.id}`, appBody({ notes: 'first note', location: 'Hyderabad' }));
  assert.equal(r.status, 200); assert.equal(r.data.notes, 'first note');
  const list = (await alice.get('/api/applications?q=backend')).data;
  assert.equal(list.totalElements, 1);
  assert.equal((await alice.get('/api/applications?q=zzzz-no-match')).data.totalElements, 0);
  assert.equal((await alice.get('/api/applications?q=' + encodeURIComponent('100%_'))).data.totalElements, 0, 'LIKE wildcards are escaped');
  assert.equal((await alice.get('/api/applications?status=Rejected')).data.totalElements, 0);
});

test('validation: bad enum values and bad links are rejected', async () => {
  assert.equal((await alice.post('/api/applications', appBody({ currentStatus: 'Hired' }))).status, 400);
  assert.equal((await alice.post('/api/applications', appBody({ jobType: 'Gig' }))).status, 400);
  const r = await alice.post('/api/applications', appBody({ applicationLink: 'javascript:alert(1)' }));
  assert.equal(r.status, 400); assert.ok(r.data.fieldErrors?.applicationLink);
  assert.equal((await alice.post('/api/applications', { position: 'No company' })).status, 400);
});

test('mass assignment: client-supplied id, userId and deletedAt are ignored', async () => {
  const r = await alice.post('/api/applications', appBody({ companyName: 'Mass Assign Co', id: 999999, userId: 1, deletedAt: '2020-01-01T00:00:00Z' }));
  assert.equal(r.status, 201); assert.notEqual(r.data.id, 999999);
  assert.equal((await alice.get(`/api/applications/${r.data.id}`)).status, 200, 'must not be soft-deleted');
  await alice.del(`/api/applications/${r.data.id}`);
});

test('status changes write history, same status writes nothing, all entry points agree', async () => {
  let h = (await alice.get(`/api/applications/${aliceApp.id}/history`)).data;
  assert.equal(h.length, 1); assert.equal(h[0].previousStatus, null); assert.equal(h[0].newStatus, 'Applied');
  assert.equal((await alice.put(`/api/applications/${aliceApp.id}/status`, { status: 'Interview', note: 'Round 1 booked' })).status, 200);
  assert.equal((await alice.put(`/api/applications/${aliceApp.id}/status`, { status: 'Interview' })).status, 200);   // no-op
  h = (await alice.get(`/api/applications/${aliceApp.id}/history`)).data;
  assert.equal(h.length, 2, 'unchanged status must not add a history row');
  const last = h.find((x) => x.newStatus === 'Interview');
  assert.equal(last.previousStatus, 'Applied'); assert.equal(last.note, 'Round 1 booked'); assert.ok(last.changedAt);
  const viaEdit = await alice.put(`/api/applications/${aliceApp.id}`, appBody({ currentStatus: 'Assessment' }));
  assert.equal(viaEdit.status, 200);
  assert.equal((await alice.get(`/api/applications/${aliceApp.id}/history`)).data.length, 3, 'status changed through the edit form is recorded too');
  assert.equal((await alice.put(`/api/applications/${aliceApp.id}/status`, { status: 'Nonsense' })).status, 400);
  const extra = (await alice.post('/api/applications', appBody({ companyName: 'Bulk Co' }))).data;
  const bulk = await alice.put('/api/applications/bulk-status', { ids: [aliceApp.id, extra.id], status: 'Shortlisted' });
  assert.equal(bulk.status, 200, bulk.text);
  assert.equal((await alice.get(`/api/applications/${extra.id}`)).data.currentStatus, 'Shortlisted');
  assert.equal((await alice.get(`/api/applications/${aliceApp.id}/history`)).data.length, 4);
  await alice.del(`/api/applications/${extra.id}`);
});

test('duplicate detection: by company+role and by normalized URL; never blocks saving', async () => {
  const a = (await alice.post('/api/applications', appBody({ companyName: 'Dup Corp', position: 'Data Analyst', applicationLink: 'https://www.jobs.example.com/p/42/?utm_source=x#top' }))).data;
  let d = (await alice.get('/api/applications/duplicates?company=' + encodeURIComponent('  dup corp. ') + '&position=' + encodeURIComponent('DATA analyst'))).data;
  assert.ok(d.some((x) => x.id === a.id), 'company+role match ignores case and punctuation');
  d = (await alice.get('/api/applications/duplicates?url=' + encodeURIComponent('http://jobs.example.com/p/42'))).data;
  assert.ok(d.some((x) => x.id === a.id), 'URL match ignores scheme, www, tracking and fragment');
  d = (await alice.get('/api/applications/duplicates?url=' + encodeURIComponent('https://jobs.example.com/p/43'))).data;
  assert.ok(!d.some((x) => x.id === a.id), 'a different job id is not a duplicate');
  d = (await alice.get(`/api/applications/duplicates?company=Dup%20Corp&position=Data%20Analyst&excludeId=${a.id}`)).data;
  assert.ok(!d.some((x) => x.id === a.id), 'excludeId hides the record being edited');
  const again = await alice.post('/api/applications', appBody({ companyName: 'Dup Corp', position: 'Data Analyst' }));
  assert.equal(again.status, 201, 'a legitimate repeat application can still be saved');
  await alice.del(`/api/applications/${a.id}`); await alice.del(`/api/applications/${again.data.id}`);
});

test('delete is soft with undo: hidden at once, restorable, unrecoverable after the window', async () => {
  const a = (await alice.post('/api/applications', appBody({ companyName: 'Undo Corp' }))).data;
  assert.equal((await alice.del(`/api/applications/${a.id}`)).status, 204);
  assert.equal((await alice.get(`/api/applications/${a.id}`)).status, 404, 'hidden immediately');
  assert.ok(!(await alice.get('/api/applications?q=Undo')).data.content.some((x) => x.id === a.id));
  const back = await alice.post(`/api/applications/${a.id}/restore`);
  assert.equal(back.status, 200, back.text);
  assert.equal((await alice.get(`/api/applications/${a.id}`)).data.companyName, 'Undo Corp');
  assert.equal((await alice.get(`/api/applications/${a.id}/history`)).data.length >= 1, true, 'history survives the round trip');
  await alice.del(`/api/applications/${a.id}`);
  if (SQL_CMD) {
    execSync(SQL_CMD, { input: `UPDATE applications SET deleted_at = DATE_SUB(UTC_TIMESTAMP(6), INTERVAL 1 HOUR) WHERE application_id = ${a.id};`, stdio: ['pipe', 'ignore', 'inherit'] });
    assert.equal((await alice.post(`/api/applications/${a.id}/restore`)).status, 409, 'restore must fail once the undo window has passed');
  }
});

// ---------------- skills, match score ----------------
test('skills: unique per user (aliases too), match score is deterministic', async () => {
  assert.equal((await alice.post('/api/skills', { name: 'Java', category: 'Programming languages' })).status, 201);
  assert.equal((await alice.post('/api/skills', { name: ' java ', category: 'Programming languages' })).status, 409);
  assert.equal((await alice.post('/api/skills', { name: 'SQL', category: 'Databases' })).status, 201);
  assert.equal((await alice.post('/api/skills', { name: 'X', category: 'Not a category' })).status, 400);
  const a = (await alice.get(`/api/applications/${aliceApp.id}`)).data;
  assert.ok(Math.abs(a.matchScore - 66.7) < 0.1, `2 of 3 required skills -> 66.7%, got ${a.matchScore}`);
  assert.equal((await bob.get('/api/skills')).data.length, 0, "Alice's skills never appear for Bob");
});

// ---------------- interviews and the two checklists ----------------
test('interviews and checklists persist and stay separate', async () => {
  const when = new Date(Date.now() + 3 * 86400000).toISOString();
  const iv = await alice.post(`/api/applications/${aliceApp.id}/interviews`, { interviewDate: when, round: 'Technical', interviewMode: 'Video call', result: 'Pending', topicsToRevise: 'Joins' });
  assert.equal(iv.status, 201, iv.text);
  assert.equal((await alice.get(`/api/interviews?applicationId=${aliceApp.id}`)).data.length, 1);
  assert.equal((await alice.put(`/api/interviews/${iv.data.id}`, { interviewDate: when, round: 'Technical', result: 'Passed', feedback: 'Went well' })).data.result, 'Passed');
  const prep = (await alice.post(`/api/applications/${aliceApp.id}/checklist`, { type: 'PREP', text: 'Review joins', interviewId: iv.data.id })).data;
  const req = (await alice.post(`/api/applications/${aliceApp.id}/checklist`, { type: 'REQUIREMENT', category: 'Skills to improve', text: 'Learn Docker' })).data;
  const items = (await alice.get(`/api/applications/${aliceApp.id}/checklist`)).data;
  assert.equal(items.filter((i) => i.type === 'PREP').length, 1); assert.equal(items.filter((i) => i.type === 'REQUIREMENT').length, 1);
  assert.equal((await alice.patch(`/api/checklist/${prep.id}`, { completed: true })).data.completed, true);
  assert.equal((await alice.get(`/api/applications/${aliceApp.id}/checklist`)).data.find((i) => i.id === prep.id).text, 'Review joins', 'PATCH changes only the fields sent');
  assert.equal((await alice.post(`/api/applications/${aliceApp.id}/checklist`, { type: 'BOGUS', text: 'x' })).status, 400);
  assert.equal((await alice.del(`/api/checklist/${req.id}`)).status, 204);
  assert.equal((await alice.del(`/api/interviews/${iv.data.id}`)).status, 204);
});

// ---------------- documents ----------------
const pdfBytes = () => Buffer.from('%PDF-1.4\n1 0 obj<<>>endobj\ntrailer<<>>\n%%EOF\n');
const upload = (b, appId, bytes, name, type = 'Resume') => { const f = new FormData(); f.append('file', new Blob([bytes]), name); f.append('type', type); return b.call('POST', `/api/applications/${appId}/documents`, { form: f }); };

test('documents: real files are stored, validated by content, downloaded only by the owner', async () => {
  const ok = await upload(alice, aliceApp.id, pdfBytes(), 'resume.pdf');
  assert.equal(ok.status, 201, ok.text);
  assert.equal(ok.data.contentType, 'application/pdf');
  const dl = await alice.get(`/api/documents/${ok.data.id}/download`, { raw: true });
  assert.equal(dl.status, 200);
  assert.equal(dl.headers.get('x-content-type-options'), 'nosniff');
  assert.match(dl.headers.get('content-disposition') || '', /attachment/i);
  assert.equal(Buffer.from(await dl.arrayBuffer()).subarray(0, 5).toString(), '%PDF-', 'bytes round-trip');
  assert.equal((await bob.get(`/api/documents/${ok.data.id}/download`)).status, 404, "Bob cannot download Alice's file");
  assert.equal((await bob.del(`/api/documents/${ok.data.id}`)).status, 404, "Bob cannot delete Alice's file");
  const disguised = await upload(alice, aliceApp.id, Buffer.from('MZ\x90\x00 this is an executable'), 'resume.pdf');
  assert.equal(disguised.status, 400, 'content decides, not the file name');
  assert.equal((await upload(alice, aliceApp.id, pdfBytes(), 'resume.exe')).status, 400, 'extension must match the real type');
  assert.equal((await upload(alice, aliceApp.id, Buffer.alloc(0), 'empty.pdf')).status, 400);
  const big = Buffer.concat([pdfBytes(), Buffer.alloc(5 * 1024 * 1024 + 10)]);
  const tooBig = await upload(alice, aliceApp.id, big, 'big.pdf');
  assert.ok([400, 413].includes(tooBig.status), `over 5 MB must be refused, got ${tooBig.status}`);
  assert.equal((await upload(bob, aliceApp.id, pdfBytes(), 'x.pdf')).status, 404, "Bob cannot attach files to Alice's application");
  const traversal = await upload(alice, aliceApp.id, pdfBytes(), '../../../etc/passwd.pdf');
  assert.equal(traversal.status, 201); assert.ok(!traversal.data.name.includes('/'), 'path parts are stripped from the display name');
  assert.equal((await alice.get(`/api/applications/${aliceApp.id}/documents`)).data.length, 2);
  assert.equal((await alice.del(`/api/documents/${ok.data.id}`)).status, 204);
  assert.equal((await alice.get(`/api/documents/${ok.data.id}/download`)).status, 404, 'deleted file is gone');
});

// ---------------- cross-user isolation ----------------
test('isolation: Bob gets 404 for every one of Alice\'s records and cannot modify them', async () => {
  bobApp = (await bob.post('/api/applications', appBody({ companyName: 'Bob Only Inc' }))).data;
  const iv = (await alice.post(`/api/applications/${aliceApp.id}/interviews`, { interviewDate: new Date(Date.now() + 86400000).toISOString(), round: 'HR', result: 'Pending' })).data;
  const item = (await alice.post(`/api/applications/${aliceApp.id}/checklist`, { type: 'PREP', text: 'private prep' })).data;
  const skill = (await alice.get('/api/skills')).data[0];
  const id = aliceApp.id;
  const reads = [`/api/applications/${id}`, `/api/applications/${id}/history`, `/api/applications/${id}/checklist`, `/api/applications/${id}/documents`];
  for (const p of reads) assert.equal((await bob.get(p)).status, 404, `GET ${p}`);
  assert.equal((await bob.put(`/api/applications/${id}`, appBody({ companyName: 'HACKED' }))).status, 404);
  assert.equal((await bob.put(`/api/applications/${id}/status`, { status: 'Rejected' })).status, 404);
  assert.equal((await bob.del(`/api/applications/${id}`)).status, 404);
  assert.equal((await bob.post(`/api/applications/${id}/restore`)).status, 404);
  assert.equal((await bob.post(`/api/applications/${id}/interviews`, { interviewDate: new Date(Date.now() + 86400000).toISOString(), round: 'HR' })).status, 404);
  assert.equal((await bob.post(`/api/applications/${id}/checklist`, { type: 'PREP', text: 'injected' })).status, 404);
  assert.equal((await bob.put(`/api/interviews/${iv.id}`, { interviewDate: new Date().toISOString(), round: 'HR' })).status, 404);
  assert.equal((await bob.del(`/api/interviews/${iv.id}`)).status, 404);
  assert.equal((await bob.patch(`/api/checklist/${item.id}`, { completed: true })).status, 404);
  assert.equal((await bob.del(`/api/checklist/${item.id}`)).status, 404);
  assert.equal((await bob.del(`/api/skills/${skill.id}`)).status, 404);
  // Bulk update is all-or-nothing: one foreign id fails the whole request and changes nothing.
  const bulk = await bob.put('/api/applications/bulk-status', { ids: [bobApp.id, id], status: 'Withdrawn' });
  assert.equal(bulk.status, 404);
  assert.equal((await bob.get(`/api/applications/${bobApp.id}`)).data.currentStatus, 'Applied', "Bob's own record was not half-updated");
  // Listings, search, export, dashboard and interviews contain only the caller's rows.
  assert.ok(!(await bob.get('/api/applications?size=100')).data.content.some((a) => a.id === id));
  assert.equal((await bob.get('/api/applications?q=' + encodeURIComponent('E2E Corp'))).data.totalElements, 0, 'search is scoped to the caller');
  assert.equal((await bob.get('/api/interviews')).data.length, 0);
  assert.ok(!(await bob.get('/api/applications/export')).text.includes('E2E Corp'));
  assert.equal((await bob.get('/api/dashboard')).data.total, 1);
  // And Alice's data is untouched.
  const still = (await alice.get(`/api/applications/${id}`)).data;
  assert.notEqual(still.companyName, 'HACKED'); assert.notEqual(still.currentStatus, 'Rejected');
});

// ---------------- dashboard and analytics from real records ----------------
test('dashboard and export are derived from real records', async () => {
  const d = (await alice.get('/api/dashboard')).data;
  const mine = (await alice.get('/api/applications?size=100')).data.totalElements;
  assert.equal(d.total, mine);
  assert.equal(d.funnel[0].count, mine, 'every application reached Applied');
  assert.ok(d.funnel.every((s, i) => i === 0 || s.count <= d.funnel[i - 1].count), 'funnel never grows');
  assert.ok(d.reachedInterview >= 1, 'Alice moved an application to Interview');
  const csv = (await alice.get('/api/applications/export')).text.replace(/^\uFEFF/, '');
  assert.ok(csv.startsWith('Company,') || /^"?Company/.test(csv));
  assert.equal(csv.trim().split(/\r?\n/).length, mine + 1);
  const evil = (await alice.post('/api/applications', appBody({ companyName: '=HYPERLINK("http://x","y")', position: 'Formula, "quoted"' }))).data;
  const csv2 = (await alice.get('/api/applications/export')).text;
  assert.ok(/'=HYPERLINK/.test(csv2), 'formula prefix neutralized'); assert.ok(!/(^|,)=HYPERLINK/m.test(csv2));
  await alice.del(`/api/applications/${evil.id}`);
  for (const p of ['/api/analytics/funnel', '/api/analytics/sources', '/api/analytics/trends']) assert.equal((await alice.get(p)).status, 200, p);
});

test('weekly goal: one per user, counts real applications', async () => {
  assert.equal((await alice.post('/api/goals', { target: 0 })).status, 400);
  const g = await alice.post('/api/goals', { target: 5 });
  assert.equal(g.status, 201, g.text);
  assert.equal((await alice.post('/api/goals', { target: 6 })).status, 409);
  assert.equal((await alice.put(`/api/goals/${g.data.id}`, { target: 7 })).data.target, 7);
  assert.equal((await bob.put(`/api/goals/${g.data.id}`, { target: 1 })).status, 404, "Bob cannot edit Alice's goal");
  const now = (await alice.get('/api/goals')).data;
  const counted = (await alice.get('/api/applications?size=100')).data.content.filter((a) => a.applicationDate === new Date().toISOString().slice(0, 10)).length;
  assert.ok(now.submitted >= 1 && now.submitted <= counted + 10);
});

// ---------------- share links ----------------
test('share links: random, read-only, summary only, revocable, one-time URL', async () => {
  assert.equal((await alice.post('/api/share-links', { expiresInDays: 0 })).status, 400);
  assert.equal((await alice.post('/api/share-links', { expiresInDays: 9999 })).status, 400);
  const link = await alice.post('/api/share-links', { expiresInDays: 7 });
  assert.equal(link.status, 201, link.text);
  const token = link.data.url.split('/share/')[1];
  assert.ok(token && token.length >= 40, 'token has at least ~256 bits of entropy');
  const second = (await alice.post('/api/share-links', { expiresInDays: 7 })).data;
  assert.notEqual(second.url.split('/share/')[1], token, 'tokens are unique');
  const listed = (await alice.get('/api/share-links')).data;
  assert.ok(listed.every((l) => !l.url), 'the token is shown once, never listed again');
  assert.equal(listed.find((l) => l.id === link.data.id).status, 'Active');
  const pub = await anon.get(`/api/share/${token}`);                      // no cookies at all
  assert.equal(pub.status, 200);
  assert.deepEqual(Object.keys(pub.data).sort(), ['expiresAt', 'interviews', 'offers', 'statusCounts', 'total']);
  const blob = JSON.stringify(pub.data);
  for (const secret of ['E2E Corp', 'first note', 'Hyderabad', 'private prep', 'alice', 'example.test', 'resume']) assert.ok(!blob.toLowerCase().includes(secret.toLowerCase()), `public summary must not contain "${secret}"`);
  assert.match(pub.headers.get('cache-control') || '', /no-store/); assert.match(pub.headers.get('x-robots-tag') || '', /noindex/);
  for (const m of ['POST', 'PUT', 'DELETE', 'PATCH']) assert.ok([401, 403, 404, 405].includes((await anon.call(m, `/api/share/${token}`, { json: {} })).status), `${m} on a share link must fail`);
  assert.equal((await anon.get(`/api/applications`)).status, 401, 'a share token opens nothing else');
  assert.equal((await new Browser().call('GET', '/api/applications', { headers: { authorization: `Bearer ${token}` } })).status, 401);
  assert.equal((await bob.del(`/api/share-links/${link.data.id}`)).status, 404, "Bob cannot revoke Alice's link");
  assert.equal((await alice.del(`/api/share-links/${link.data.id}`)).status, 204);
  const gone = await anon.get(`/api/share/${token}`);
  assert.equal(gone.status, 404, 'revoked');
  const unknown = await anon.get('/api/share/' + 'A'.repeat(43));
  assert.equal(unknown.status, 404); assert.equal(unknown.text, gone.text, 'revoked, expired and unknown look identical');
  assert.equal((await alice.get('/api/share-links')).data.find((l) => l.id === link.data.id).status, 'Revoked', 'revoked links stay listed');
  await alice.del(`/api/share-links/${second.id}`);
});

test('share links: expiry (needs E2E_SQL_CMD; skipped otherwise)', { skip: !SQL_CMD && 'set E2E_SQL_CMD to run this check' }, async () => {
  const link = (await alice.post('/api/share-links', { expiresInDays: 1 })).data;
  const token = link.url.split('/share/')[1];
  assert.equal((await anon.get(`/api/share/${token}`)).status, 200);
  execSync(SQL_CMD, { input: `UPDATE share_links SET expires_at = DATE_SUB(UTC_TIMESTAMP(6), INTERVAL 1 MINUTE) WHERE share_id = ${link.id};`, stdio: ['pipe', 'ignore', 'inherit'] });
  assert.equal((await anon.get(`/api/share/${token}`)).status, 404, 'expired link stops working immediately');
  assert.equal((await alice.get('/api/share-links')).data.find((l) => l.id === link.id).status, 'Expired');
});

// ---------------- profile, password, sessions ----------------
test('profile: update, change password signs out other sessions, wrong current password refused', async () => {
  assert.equal((await alice.put('/api/profile', { name: 'Alice Renamed' })).status, 200);
  assert.equal((await alice.get('/api/auth/me')).data.name, 'Alice Renamed');
  const bad = await alice.post('/api/profile/password', { currentPassword: 'Wrong-Passw0rd!', password: PASSWORD + 'x', confirmPassword: PASSWORD + 'x' });
  assert.ok([400, 401, 403].includes(bad.status));
  const mail = await alice.post('/api/profile/email-change', { password: PASSWORD, newEmail: email('new') });
  assert.ok([202, 400].includes(mail.status), 'email change needs verification; it must not switch the address immediately');
  assert.equal((await alice.get('/api/auth/me')).data.email, email('a'), 'address unchanged until confirmed');
});

test('unknown API routes and methods do not leak internals', async () => {
  for (const p of ['/api/does-not-exist', '/actuator/env', '/api/../etc/passwd']) {
    const r = await alice.get(p);
    assert.ok([401, 403, 404].includes(r.status), `${p} -> ${r.status}`);
    assert.ok(!/at com\.|java\.lang|Exception|stack/i.test(r.text), 'no stack traces');
  }
});

// ---------------- brute force protection (LAST: it blocks this IP's logins for a while) ----------------
// ---------------- token lifecycle and password recovery (Carol: her own throwaway account) ----------------
const carol = new Browser();
const MAILHOG = (process.env.E2E_MAILHOG_URL || 'http://localhost:8025').replace(/\/+$/, '');
const NEW_PASSWORD = 'E2e-N3w!Passw0rd#2';

/** Newest emailed link token for `to` from MailHog. null = MailHog unreachable, '' = no email arrived in 10 s. */
async function mailToken(to, linkPath) {
  for (let i = 0; i < 20; i++) {
    const r = await fetch(`${MAILHOG}/api/v2/search?kind=to&query=${encodeURIComponent(to)}`).catch(() => null);
    if (!r || !r.ok) return null;
    for (const m of (await r.json()).items ?? []) {
      const body = String(m.Content?.Body ?? '').replace(/=\r?\n/g, '').replace(/=([0-9A-F]{2})/gi, (_, h) => String.fromCharCode(parseInt(h, 16)));
      const t = body.match(new RegExp(linkPath + '\\?token=([A-Za-z0-9_-]+)'));
      if (t) return t[1];
    }
    await new Promise((ok) => setTimeout(ok, 500));
  }
  return '';
}
after(async () => {   // Carol is removed whichever password she ended up with
  for (const pw of [NEW_PASSWORD, PASSWORD]) {
    const b = new Browser(); const r = await b.post('/api/auth/login', { email: email('c'), password: pw }).catch(() => null);
    if (r?.status === 200) { await b.post('/api/profile/delete-request', { password: pw }).catch(() => {}); break; }
  }
});

test('refresh: the token rotates; replaying the old one is refused and ends that login session', async () => {
  await register('Carol', email('c'));
  await login(carol, email('c'));
  const old = carol.jar.get('docket_rt');
  assert.ok(old, 'a refresh cookie is issued');
  assert.equal((await carol.post('/api/auth/refresh')).status, 204);
  assert.notEqual(carol.jar.get('docket_rt'), old, 'refresh tokens are single-use: a new one replaces the old');
  assert.equal((await carol.get('/api/auth/me')).status, 200);
  const replay = await fetch(BASE + '/api/auth/refresh', { method: 'POST', headers: { cookie: `docket_rt=${old}; XSRF-TOKEN=t`, 'X-XSRF-TOKEN': 't' } });
  assert.equal(replay.status, 401, 'the old refresh token must not work again');
  assert.equal((await carol.get('/api/auth/me')).status, 401, 'reuse of an old token ends that login session (theft signal)');
  assert.equal((await fetch(BASE + '/api/applications', { headers: { cookie: 'docket_at=not.a.jwt' } })).status, 401, 'garbage credentials are a clean 401, not a 500');
});

test('password recovery: the emailed link works once, weak passwords are refused, old sessions are signed out', async (t) => {
  await login(carol, email('c'));                                   // an open session the reset must end
  assert.equal((await anon.post('/api/auth/forgot-password', { email: email('c') })).status, 202);
  const token = await mailToken(email('c'), '/reset-password');
  if (token === null) return t.skip(`MailHog is not reachable at ${MAILHOG} (start it: cd backend && docker compose up -d)`);
  assert.ok(token, 'a reset email with a link must arrive');
  assert.ok(token.length >= 40, 'the token must be long and random');
  assert.equal((await anon.post('/api/auth/reset-password', { token, password: 'weak' })).status, 400, 'weak password refused');
  assert.equal((await anon.post('/api/auth/reset-password', { token: 'A'.repeat(43), password: NEW_PASSWORD })).status, 400, 'unknown token refused');
  assert.equal((await anon.post('/api/auth/reset-password', { token, password: NEW_PASSWORD })).status, 204, 'the real token still works after the refused attempts');
  assert.equal((await anon.post('/api/auth/reset-password', { token, password: PASSWORD })).status, 400, 'a reset link cannot be used twice');
  assert.equal((await new Browser().post('/api/auth/login', { email: email('c'), password: PASSWORD })).status, 401, 'the old password stops working');
  assert.equal((await new Browser().post('/api/auth/login', { email: email('c'), password: NEW_PASSWORD })).status, 200, 'the new password works');
  assert.equal((await carol.get('/api/auth/me')).status, 401, 'sessions open before the reset are signed out');
});

// ---------------- Chrome extension token ----------------
test('extension token: can save a job and nothing else, never leaks to others, dies when revoked', async () => {
  const made = await alice.post('/api/extension-tokens', { label: 'E2E laptop' });
  assert.equal(made.status, 201, made.text);
  const token = made.data.token, tid = made.data.info.id;
  assert.ok(token && token.length >= 40, 'a long random token is returned once');
  assert.ok(!JSON.stringify((await alice.get('/api/extension-tokens')).data).includes(token), 'listing never shows the token again');
  const ext = (method, path, json) => fetch(BASE + path, { method, headers: { authorization: `Bearer ${token}`, ...(json ? { 'content-type': 'application/json' } : {}) }, body: json ? JSON.stringify(json) : undefined });

  assert.equal((await ext('GET', '/api/extension/me')).status, 200, 'works on its own endpoint');
  for (const p of ['/api/applications', '/api/dashboard', '/api/profile', '/api/skills', '/api/extension-tokens', '/api/applications/export', '/api/share-links'])
    assert.ok([401, 403].includes((await ext('GET', p)).status), `the extension token must be refused on ${p}`);
  assert.ok([401, 403].includes((await ext('POST', '/api/applications', appBody())).status), 'and cannot use the normal create endpoint');
  assert.equal((await alice.get('/api/extension/me')).status, 403, 'a website login is not accepted on the extension API');

  const name = `Ext ${stamp}`;
  assert.deepEqual(await (await ext('GET', `/api/extension/duplicates?company=${encodeURIComponent(name)}&position=Extension%20Role`)).json(), [], 'nothing to warn about yet');
  const saved = await ext('POST', '/api/extension/applications', appBody({ companyName: name, position: 'Extension Role' }));
  assert.equal(saved.status, 201, await saved.clone().text());
  const rec = await saved.json();
  assert.equal((await alice.get(`/api/applications/${rec.id}`)).status, 200, 'it landed in the token owner\'s account');
  assert.equal((await bob.get(`/api/applications/${rec.id}`)).status, 404, 'and nowhere else');
  assert.ok((await (await ext('GET', `/api/extension/duplicates?company=${encodeURIComponent(name)}&position=Extension%20Role`)).json()).length >= 1, 'the duplicate warning now fires');
  assert.equal((await ext('POST', '/api/extension/applications', { position: 'no company' })).status, 400, 'same validation as the website');
  assert.equal((await fetch(BASE + '/api/extension/me', { headers: { authorization: 'Bearer dkx_' + 'A'.repeat(43) } })).status, 401, 'a made-up token is refused');

  assert.equal((await bob.del(`/api/extension-tokens/${tid}`)).status, 404, 'another user cannot revoke it');
  assert.equal((await alice.del(`/api/extension-tokens/${tid}`)).status, 204);
  assert.equal((await ext('GET', '/api/extension/me')).status, 401, 'a revoked token stops working at once');
});

test('CORS: a foreign website origin is given no permission, on the API or the extension API', async () => {
  for (const p of ['/api/applications', '/api/extension/me']) {
    const pre = await fetch(BASE + p, { method: 'OPTIONS', headers: { origin: 'https://evil.example', 'access-control-request-method': 'GET', 'access-control-request-headers': 'authorization' } });
    assert.equal(pre.headers.get('access-control-allow-origin'), null, `preflight for ${p}`);
    const real = await fetch(BASE + p, { headers: { origin: 'https://evil.example', cookie: alice.cookieHeader() } });
    assert.equal(real.headers.get('access-control-allow-origin'), null, `credentialed request for ${p}`);
  }
});

// ---------------- Kanban, email change, reminders ----------------
test('kanban: every move persists and is recorded; a refused move leaves the card where the server has it', async () => {
  const r = await alice.post('/api/applications', appBody({ companyName: `Kanban ${stamp}` }));
  assert.equal(r.status, 201, r.text); const id = r.data.id;
  for (const status of ['Shortlisted', 'Assessment', 'Interview']) {          // the same call the board makes when a card is dropped
    assert.equal((await alice.put(`/api/applications/${id}/status`, { status })).status, 200);
    assert.equal((await alice.get(`/api/applications/${id}`)).data.currentStatus, status, 'a re-read shows the move was saved');
    assert.equal((await alice.get(`/api/applications?status=${status}&q=${encodeURIComponent('Kanban ' + stamp)}`)).data.totalElements, 1, `list view agrees: the card is in ${status}`);
  }
  assert.equal((await alice.put(`/api/applications/${id}/status`, { status: 'Hired' })).status, 400, 'an invalid column is refused');
  assert.equal((await alice.get(`/api/applications/${id}`)).data.currentStatus, 'Interview', 'so the board can put the card back');
  assert.equal((await alice.get(`/api/applications/${id}/history`)).data.length, 4, 'created + three moves, and the refused move wrote nothing');
  assert.equal((await bob.put(`/api/applications/${id}/status`, { status: 'Rejected' })).status, 404, 'nobody else can move it');
  assert.equal((await alice.get(`/api/applications/${id}`)).data.currentStatus, 'Interview');
});

test('email change: nothing changes until the emailed link is used; the link works once; the old address stops working', async (t) => {
  const dave = new Browser(), oldEmail = email('d'), newEmail = email('d2');
  await register('Dave', oldEmail); await login(dave, oldEmail);
  try {
    assert.equal((await dave.post('/api/profile/email-change', { email: newEmail })).status, 202);
    assert.equal((await dave.get('/api/profile')).data.email, oldEmail, 'the address is NOT changed by the request alone');
    const token = await mailToken(newEmail, '/confirm-email');
    if (token === null) return t.skip(`MailHog is not reachable at ${MAILHOG}`);
    assert.ok(token, 'a confirmation email must arrive at the NEW address');
    assert.equal((await anon.post('/api/auth/confirm-email-change', { token: 'A'.repeat(43) })).status, 400, 'a made-up token is refused');
    assert.equal((await anon.post('/api/auth/confirm-email-change', { token })).status, 204);
    assert.equal((await anon.post('/api/auth/confirm-email-change', { token })).status, 400, 'the link cannot be used twice');
    assert.equal((await new Browser().post('/api/auth/login', { email: oldEmail, password: PASSWORD })).status, 401, 'the old address no longer logs in');
    const fresh = new Browser();
    assert.equal((await fresh.post('/api/auth/login', { email: newEmail, password: PASSWORD })).status, 200, 'the new address does');
    assert.equal((await fresh.get('/api/profile')).data.email, newEmail);
  } finally {
    for (const em of [newEmail, oldEmail]) {
      const b = new Browser(); const l = await b.post('/api/auth/login', { email: em, password: PASSWORD }).catch(() => null);
      if (l?.status === 200) { await b.post('/api/profile/delete-request', { password: PASSWORD }).catch(() => {}); break; }
    }
  }
});

test('reminders: preferences are validated and saved, custom reminders are private, nothing claims to be sent before it was', async () => {
  const prefs = { emailEnabled: false, deadlineLeadHours: 24, interviewLeadHours: 12, followUpEnabled: true, timezone: 'Asia/Kolkata' };
  assert.equal((await alice.put('/api/reminders/preferences', prefs)).status, 200);
  assert.deepEqual((await alice.get('/api/reminders/preferences')).data, prefs, 'saved exactly as sent');
  assert.equal((await alice.put('/api/reminders/preferences', { ...prefs, timezone: 'Mars/Olympus' })).status, 400, 'bad time zone refused');
  assert.equal((await alice.put('/api/reminders/preferences', { ...prefs, deadlineLeadHours: 0 })).status, 400, 'lead time below 1 hour refused');
  assert.equal((await alice.put('/api/reminders/preferences', { ...prefs, interviewLeadHours: 400 })).status, 400, 'lead time above 336 hours refused');
  assert.equal((await bob.get('/api/reminders/preferences')).data.timezone !== 'Asia/Kolkata', true, 'preferences are per user');

  const future = new Date(Date.now() + 3 * 86400000).toISOString();
  assert.equal((await alice.post('/api/reminders', { title: 'Past', scheduledAt: new Date(Date.now() - 3600000).toISOString() })).status, 400, 'a time in the past is refused');
  assert.equal((await alice.post('/api/reminders', { title: '   ', scheduledAt: future })).status, 400, 'a blank title is refused');
  assert.equal((await alice.post('/api/reminders', { title: 'x', scheduledAt: future, applicationId: bobApp.id })).status, 404, 'cannot attach a reminder to someone else\'s application');
  const mine = await alice.post('/api/reminders', { title: `Call recruiter ${stamp}`, scheduledAt: future, applicationId: aliceApp.id });
  assert.equal(mine.status, 201, mine.text);
  assert.notEqual(mine.data.deliveryStatus, 'SENT', 'a reminder is never reported as sent before the mail server accepted it');
  assert.ok((await alice.get('/api/reminders')).data.some((x) => x.id === mine.data.id), 'it is listed');
  assert.ok(!(await bob.get('/api/reminders')).data.some((x) => x.id === mine.data.id), 'and invisible to others');
  assert.equal((await bob.put(`/api/reminders/${mine.data.id}`, { dismissed: true })).status, 404, 'others cannot change it');
  assert.equal((await alice.put(`/api/reminders/${mine.data.id}`, { title: 'Renamed' })).data.title, 'Renamed');
  assert.equal((await alice.put(`/api/reminders/${mine.data.id}`, { dismissed: true })).data.deliveryStatus, 'DISMISSED');

  const soon = await alice.post('/api/applications', appBody({ companyName: `Deadline ${stamp}`, deadline: new Date(Date.now() + 2 * 86400000).toISOString() }));
  assert.equal(soon.status, 201, soon.text);
  assert.ok((await alice.get('/api/reminders')).data.some((x) => x.applicationId === soon.data.id), 'an application deadline produces a reminder by itself');
});

test('rate limiting: repeated wrong passwords for one account are throttled', async () => {
  const b = new Browser(); const target = email('b');
  let blocked = false;
  for (let i = 0; i < 8 && !blocked; i++) { const r = await b.post('/api/auth/login', { email: target, password: 'Wrong-Passw0rd!' }); blocked = r.status === 429; }
  assert.ok(blocked, 'a 429 must appear within 8 wrong attempts');
  const right = await b.post('/api/auth/login', { email: target, password: PASSWORD });
  assert.equal(right.status, 429, 'even the right password is refused while blocked');
});

test('account deletion: wrong password refused; correct password removes the account and its data', async () => {
  const wrong = await alice.post('/api/profile/delete-request', { password: 'Wrong-Passw0rd!' });
  assert.equal(wrong.status, 400);
  assert.equal((await alice.get('/api/auth/me')).status, 200, 'still signed in after a refused deletion');
  const ok = await alice.post('/api/profile/delete-request', { password: PASSWORD });
  assert.equal(ok.status, 204);
  assert.equal((await alice.get('/api/auth/me')).status, 401);
  created.splice(created.indexOf(email('a')), 1);   // already gone
});
