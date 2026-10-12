// Browser checks for what Vitest/jsdom cannot see: real layout at phone/tablet widths, real focus movement, real reduced-motion.
// NOT run where this was written (no browser, no registry). Run it yourself:
//   cd frontend && npm run build && npm i --no-save playwright && npx playwright install chromium && npm run test:ui
// It starts `vite preview` itself. The API is stubbed with EMPTY lists and 401s only; the single synthetic object is a test
// account returned by /auth/me so the signed-in shell can render. No applications, interviews or statistics are faked.
import { chromium } from 'playwright';
import { spawn } from 'node:child_process';

const PORT = process.env.UI_PORT || '4173';
const BASE = `http://127.0.0.1:${PORT}`;
let pass = 0, fail = 0;
const ok = (c, m) => { if (c) { pass++; console.log('  ok  ', m); } else { fail++; console.log('  FAIL', m); } };

const server = spawn('npx', ['vite', 'preview', '--port', PORT, '--strictPort', '--host', '127.0.0.1'], { stdio: 'ignore' });
async function waitUp() {
  for (let i = 0; i < 60; i++) { try { if ((await fetch(BASE)).ok) return; } catch { /* not up yet */ } await new Promise((r) => setTimeout(r, 500)); }
  throw new Error('preview server did not start (run `npm run build` first)');
}

const EMPTY_PAGE = { content: [], totalElements: 0, totalPages: 0, page: 0, size: 20 };
const EMPTY_DASH = { total: 0, thisWeek: 0, interviewsScheduled: 0, offers: 0, upcomingDeadlines: 0, reachedInterview: 0, funnel: [], sources: [], recent: [], actions: [], statuses: [], skillMatch: [], insights: {} };

/** authed=false: every private call is 401. authed=true: only /auth/me returns the test account; all data is empty. */
async function stub(page, authed) {
  await page.route('**/api/**', (route) => {
    const p = new URL(route.request().url()).pathname.replace(/^.*\/api/, '');
    const json = (status, body) => route.fulfill({ status, contentType: 'application/json', body: JSON.stringify(body) });
    if (p === '/auth/csrf') return route.fulfill({ status: 204, headers: { 'set-cookie': 'XSRF-TOKEN=t; Path=/' } });
    if (p === '/auth/me') return authed ? json(200, { id: 1, name: 'UI Test', email: 'ui-test@example.invalid' }) : json(401, { message: 'Your session has ended. Log in again.', fieldErrors: {} });
    if (p === '/auth/refresh' || !authed) return json(401, { message: 'Your session has ended. Log in again.', fieldErrors: {} });
    if (p.startsWith('/dashboard')) return json(200, p === '/dashboard' ? EMPTY_DASH : []);
    if (p.startsWith('/applications') && !/\/(duplicates|suggestions|export)/.test(p)) return json(200, EMPTY_PAGE);
    if (p.startsWith('/applications/suggestions')) return json(200, { companies: [], roles: [] });
    if (p.startsWith('/goals')) return json(200, {});
    if (p.startsWith('/analytics')) return json(200, []);
    return json(200, route.request().method() === 'GET' ? [] : {});
  });
}
const overflow = (page) => page.evaluate(() => document.documentElement.scrollWidth > document.documentElement.clientWidth + 1);
const ctx = async (browser, opts, authed) => { const c = await browser.newContext(opts); const p = await c.newPage(); await stub(p, authed); return { c, p }; };

try {
  await waitUp();
  const browser = await chromium.launch();

  console.log('Public pages: no horizontal scroll at phone, tablet and desktop widths');
  for (const w of [360, 768, 1280]) {
    for (const path of ['/', '/login', '/register', '/forgot-password']) {
      const { c, p } = await ctx(browser, { viewport: { width: w, height: 800 } }, false);
      await p.goto(BASE + path); await p.waitForLoadState('networkidle');
      ok(!(await overflow(p)), `${path} fits at ${w}px`);
      await c.close();
    }
  }

  console.log('Routing and CTAs');
  {
    const { c, p } = await ctx(browser, {}, false);
    await p.goto(`${BASE}/app`); await p.waitForURL(/\/login/, { timeout: 8000 }).catch(() => {});
    ok(/\/login/.test(p.url()), 'a signed-out visitor opening /app is sent to /login');
    await p.goto(BASE); await p.getByRole('link', { name: /create free account/i }).first().click();
    ok(/\/register/.test(p.url()), 'landing "Create Free Account" goes to /register (never straight into the dashboard)');
    await c.close();
  }

  console.log('Reduced motion');
  {
    const { c, p } = await ctx(browser, { reducedMotion: 'reduce' }, false);
    await p.goto(BASE); await p.waitForLoadState('networkidle');
    const looping = await p.evaluate(() => document.getAnimations().filter((a) => a.playState === 'running' && a.effect?.getComputedTiming().iterations === Infinity).length);
    ok(looping === 0, 'nothing loops on the landing page when the OS asks for reduced motion');
    await c.close();
  }

  console.log('Signed-in shell (stubbed, empty data) at desktop width');
  {
    const { c, p } = await ctx(browser, { viewport: { width: 1280, height: 800 } }, true);
    await p.goto(`${BASE}/app`); await p.waitForSelector('[data-search-input]', { timeout: 8000 });
    await p.keyboard.press('Tab');
    ok(await p.evaluate(() => /skip to main content/i.test(document.activeElement?.textContent || '')), 'the first Tab stop is "Skip to main content"');
    await p.evaluate(() => document.activeElement?.blur());
    await p.keyboard.press('/');
    ok(await p.evaluate(() => document.activeElement?.hasAttribute('data-search-input')), '"/" focuses the search box');
    await p.evaluate(() => document.activeElement?.blur()); await p.keyboard.press('n'); await p.waitForURL(/applications\/new/, { timeout: 4000 }).catch(() => {});
    ok(/applications\/new/.test(p.url()), '"n" opens Add application');
    await p.goto(`${BASE}/app`); await p.waitForSelector('[data-search-input]');
    await p.focus('[data-search-input]'); await p.keyboard.type('nnn/?');
    ok(!/applications\/new/.test(p.url()) && (await p.inputValue('[data-search-input]')) === 'nnn/?', 'typing n, / and ? into a field neither navigates nor is swallowed');
    await p.evaluate(() => document.activeElement?.blur()); await p.keyboard.press('?');
    await p.getByRole('dialog').waitFor({ timeout: 3000 }).catch(() => {});
    ok(await p.getByRole('dialog').count() === 1, '"?" opens the shortcut reference');
    let escaped = false;
    for (let i = 0; i < 15; i++) { await p.keyboard.press('Tab'); if (!(await p.evaluate(() => !!document.activeElement?.closest('[role="dialog"]')))) { escaped = true; break; } }
    ok(!escaped, 'Tab stays inside the open dialog (focus trap)');
    await p.keyboard.press('n');
    ok(!/applications\/new/.test(p.url()), '"n" is ignored while a dialog is open');
    await p.keyboard.press('Escape');
    ok(await p.getByRole('dialog').count() === 0, 'Escape closes the dialog');
    await p.goto(`${BASE}/app/applications`); await p.waitForLoadState('networkidle');
    ok(await p.locator('text=No applications yet').count() > 0, 'a new account sees an honest empty state, not sample rows');
    await c.close();
  }

  console.log('Signed-in shell at phone and tablet widths');
  for (const [w, h] of [[360, 740], [768, 1024]]) {
    for (const path of ['/app', '/app/applications', '/app/applications/new', '/app/board', '/app/settings']) {
      const { c, p } = await ctx(browser, { viewport: { width: w, height: h } }, true);
      await p.goto(BASE + path); await p.waitForLoadState('networkidle');
      ok(!(await overflow(p)), `${path} has no page-level horizontal scroll at ${w}px`);
      if (w === 360 && path === '/app') ok(await p.getByRole('button', { name: 'Open navigation' }).count() > 0, 'phones get an "Open navigation" button');
      await c.close();
    }
  }
  await browser.close();
} catch (e) { fail++; console.error(e); }
finally { server.kill(); }
console.log(`\n${pass} passed, ${fail} failed`); process.exit(fail ? 1 : 0);
