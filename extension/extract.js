/*
 * Reads job details from the page you are viewing. Injected on demand into the ACTIVE tab only
 * (activeTab + scripting, after you click the extension). Prefers schema.org JobPosting JSON-LD,
 * then Open Graph / meta, then site-specific DOM for LinkedIn Jobs and Google Careers.
 * Never invents values: missing fields stay empty for the user to fill in.
 * Must stay self-contained: chrome.scripting serializes this function into the page.
 */
function docketExtract() {
  const out = { found: false, fields: {}, pageTitle: (document.title || '').slice(0, 200), url: location.href };
  const f = out.fields;
  const str = (v) => (typeof v === 'string' ? v.trim() : '');
  const set = (key, val, max) => {
    const s = str(val);
    if (!s || f[key]) return;
    f[key] = s.slice(0, max || 150);
  };
  const textFromHtml = (html) => {
    if (typeof html !== 'string' || !html) return '';
    try {
      const d = new DOMParser().parseFromString(html, 'text/html');
      d.querySelectorAll('br').forEach((b) => b.replaceWith('\n'));
      d.querySelectorAll('p,li,div,h1,h2,h3,h4,h5,h6,tr').forEach((n) => n.append('\n'));
      d.querySelectorAll('li').forEach((n) => n.prepend('• '));
      return (d.body.textContent || '').replace(/[ \t\u00a0]+/g, ' ').replace(/ ?\n ?/g, '\n').replace(/\n{3,}/g, '\n\n').trim();
    } catch (e) { return ''; }
  };
  const textOf = (el) => {
    if (!el) return '';
    return (el.innerText || el.textContent || '').replace(/\s+/g, ' ').trim();
  };
  const qs = (sel) => document.querySelector(sel);
  const host = (location.hostname || '').toLowerCase();

  // --- 1. schema.org JobPosting (JSON-LD) ---
  const collect = (node, acc) => {
    if (!node || typeof node !== 'object') return;
    if (Array.isArray(node)) { node.forEach((n) => collect(n, acc)); return; }
    const t = node['@type'];
    if (t === 'JobPosting' || (Array.isArray(t) && t.includes('JobPosting'))) acc.push(node);
    if (node['@graph']) collect(node['@graph'], acc);
  };
  const found = [];
  document.querySelectorAll('script[type="application/ld+json"]').forEach((s) => {
    try { collect(JSON.parse(s.textContent), found); } catch (e) { /* skip malformed */ }
  });
  const j = found[0];
  if (j) {
    const org = j.hiringOrganization;
    const company = typeof org === 'string' ? org : str(org && org.name);
    if (company) set('companyName', company);
    if (str(j.title)) set('position', str(j.title));
    const desc = textFromHtml(j.description);
    if (desc) f.jobDescription = desc.slice(0, 20000);
    const locs = [].concat(j.jobLocation || []).map((l) => {
      if (!l) return '';
      if (typeof l === 'string') return l;
      const a = l.address;
      if (!a) return str(l.name);
      if (typeof a === 'string') return a;
      return [a.addressLocality, a.addressRegion, a.addressCountry && (a.addressCountry.name || a.addressCountry)]
        .filter((x) => typeof x === 'string' && x).join(', ');
    }).filter(Boolean);
    if (locs.length) set('location', locs[0]);
    if (String(j.jobLocationType || '').toUpperCase() === 'TELECOMMUTE') f.workMode = 'Remote';
    const et = [].concat(j.employmentType || []).map((x) => String(x).toUpperCase().replace(/[^A-Z]/g, ''));
    if (et.includes('INTERN') || et.includes('INTERNSHIP')) f.jobType = 'Internship';
    else if (et.includes('FULLTIME')) f.jobType = 'Full-time';
    else if (et.includes('PARTTIME')) f.jobType = 'Part-time';
    else if (et.length) f.jobType = 'Other';
    if (str(j.validThrough)) {
      const d = new Date(j.validThrough);
      if (!isNaN(d)) f.deadline = d.toISOString();
    }
    const sk = typeof j.skills === 'string' ? j.skills
      : Array.isArray(j.skills) ? j.skills.filter((x) => typeof x === 'string').join(',') : '';
    const skills = sk.split(/[,;\n]/).map((x) => x.trim()).filter((x) => x && x.length <= 80).slice(0, 50);
    if (skills.length) f.requiredSkills = skills;
  }

  // --- 2. Open Graph / standard meta ---
  const meta = (prop) => {
    const el = document.querySelector(`meta[property="${prop}"], meta[name="${prop}"]`);
    return el ? str(el.getAttribute('content')) : '';
  };
  set('position', meta('og:title') || meta('twitter:title'));
  // og:title often includes " | Company" — leave as-is unless empty
  const ogDesc = meta('og:description') || meta('description');
  if (ogDesc && !f.jobDescription) f.jobDescription = ogDesc.slice(0, 20000);
  set('companyName', meta('og:site_name'));

  // --- 3. LinkedIn Jobs detail pages ---
  // Prefer current job URL (view/ paths), never invent from search lists.
  const isLinkedIn = host.includes('linkedin.com');
  if (isLinkedIn) {
    // Canonical / current URL cleanup for /jobs/view/
    const viewMatch = location.pathname.match(/\/jobs\/view\/(\d+)/);
    if (viewMatch) {
      out.url = location.origin + '/jobs/view/' + viewMatch[1] + '/';
    }
    const liTitle = qs('.job-details-jobs-unified-top-card__job-title h1')
      || qs('.job-details-jobs-unified-top-card__job-title')
      || qs('h1.t-24')
      || qs('.top-card-layout__title')
      || qs('h1');
    set('position', textOf(liTitle));

    const liCompany = qs('.job-details-jobs-unified-top-card__company-name a')
      || qs('.job-details-jobs-unified-top-card__company-name')
      || qs('a.topcard__org-name-link')
      || qs('.topcard__flavor a')
      || qs('.job-details-jobs-unified-top-card__primary-description-container a');
    set('companyName', textOf(liCompany));

    const liLoc = qs('.job-details-jobs-unified-top-card__tertiary-description-container span')
      || qs('.job-details-jobs-unified-top-card__bullet')
      || qs('.topcard__flavor--bullet')
      || qs('.jobs-unified-top-card__bullet');
    // Location often sits among preference bullets — take first that looks like a place
    if (!f.location) {
      const bullets = document.querySelectorAll(
        '.job-details-jobs-unified-top-card__primary-description-container span, .job-details-jobs-unified-top-card__tertiary-description-container span, .topcard__flavor--bullet, .jobs-unified-top-card__bullet'
      );
      for (const b of bullets) {
        const t = textOf(b);
        if (t && t.length < 120 && !/applicant|ago|people|click|promoted/i.test(t)) {
          set('location', t);
          break;
        }
      }
    }

    const liDesc = qs('#job-details')
      || qs('.jobs-description__content')
      || qs('.jobs-box__html-content')
      || qs('.description__text')
      || qs('article.jobs-description');
    if (liDesc && !f.jobDescription) {
      const t = (liDesc.innerText || '').replace(/\n{3,}/g, '\n\n').trim();
      if (t) f.jobDescription = t.slice(0, 20000);
    }

    // Work mode / type from preference pills
    const pills = Array.from(document.querySelectorAll(
      'button.job-details-fit-level-preferences, .job-details-fit-level-preferences, .jobs-unified-top-card__job-insight span, .description__job-criteria-text, li.jobs-unified-top-card__job-insight'
    )).map(textOf).filter(Boolean);
    for (const p of pills) {
      if (!f.workMode && /remote/i.test(p)) f.workMode = 'Remote';
      else if (!f.workMode && /hybrid/i.test(p)) f.workMode = 'Hybrid';
      else if (!f.workMode && /on-?site|office/i.test(p)) f.workMode = 'Onsite';
      if (!f.jobType && /intern/i.test(p)) f.jobType = 'Internship';
      else if (!f.jobType && /full-?time/i.test(p)) f.jobType = 'Full-time';
      else if (!f.jobType && /part-?time/i.test(p)) f.jobType = 'Part-time';
    }
    if (!f.applicationSource) f.applicationSource = 'LinkedIn';
  }

  // --- 4. Google Careers ---
  const isGoogleCareers = host.includes('careers.google.com') || host.includes('google.com') && location.pathname.includes('/jobs/');
  if (isGoogleCareers || host.includes('careers.google.com')) {
    const gTitle = qs('h1[data-test-id="job-title"], h1.p1N2lc, h1');
    set('position', textOf(gTitle));
    set('companyName', 'Google');
    const gLoc = qs('[data-test-id="job-location"], span.rQUL0d, .WpHeLc');
    set('location', textOf(gLoc));
    const gDesc = qs('[data-test-id="job-description"], .aG5W3, div[jsname]');
    // Prefer longest text block under main
    if (!f.jobDescription) {
      let best = '';
      document.querySelectorAll('div[data-test-id="job-description"], .aG5W3, main div').forEach((el) => {
        const t = (el.innerText || '').trim();
        if (t.length > best.length && t.length < 50000) best = t;
      });
      if (best.length > 80) f.jobDescription = best.slice(0, 20000);
    }
    if (!f.applicationSource) f.applicationSource = 'Company career page';
  }

  // --- 5. Generic career-page heuristics (h1 + company in title) ---
  if (!f.position) {
    const h1 = qs('h1');
    set('position', textOf(h1));
  }
  if (!f.companyName) {
    // "Role at Company" / "Role | Company" patterns in title
    const t = document.title || '';
    const m = t.match(/\s+(?:at|@|\||–|-|—)\s+([^|–—\-]+)$/i);
    if (m) set('companyName', m[1].replace(/\s*[-|].*$/, '').trim());
  }
  if (!f.jobDescription) {
    const main = qs('article, [role="main"], main, .job-description, #job-description, .description');
    if (main) {
      const t = (main.innerText || '').replace(/\n{3,}/g, '\n\n').trim();
      if (t.length > 80) f.jobDescription = t.slice(0, 20000);
    }
  }

  // Prefer tab URL for non-LinkedIn; LinkedIn already normalized when possible
  if (!isLinkedIn || !out.url) out.url = location.href;

  out.found = Object.keys(f).length > 0;
  return out;
}
if (typeof globalThis !== 'undefined') globalThis.docketExtract = docketExtract;
