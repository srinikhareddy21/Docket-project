// Run: NODE_PATH=<folder with jsdom installed> node test/extract.test.js
const { JSDOM } = require('jsdom');
const fs = require('fs'), path = require('path');
const src = fs.readFileSync(path.join(__dirname, '..', 'extract.js'), 'utf8');
let pass = 0, fail = 0;
const ok = (c, m) => { if (c) pass++; else { fail++; console.log('FAIL:', m); } };

function run(html, url = 'https://jobs.example.test/p/1') {
  const dom = new JSDOM(html, { url, runScripts: 'outside-only' });
  dom.window.eval(src);
  return dom.window.eval('docketExtract()');
}
const ld = (o) => `<script type="application/ld+json">${JSON.stringify(o)}</script>`;

let r = run('<html><head><title>x</title></head><body>no data</body></html>');
ok(!r.found && Object.keys(r.fields).length === 0, 'no structured data -> nothing found, nothing invented');

r = run(`<html><head>${ld({ '@context': 'https://schema.org', '@type': 'JobPosting', title: 'Backend Intern', hiringOrganization: { name: 'Acme' },
  description: '<p>Build <b>APIs</b></p><ul><li>Java</li><li>SQL</li></ul>', employmentType: 'INTERN', jobLocationType: 'TELECOMMUTE',
  jobLocation: { address: { addressLocality: 'Pune', addressRegion: 'MH', addressCountry: 'IN' } }, validThrough: '2030-01-02T00:00:00Z', skills: 'Java, SQL; Git' })}</head></html>`);
ok(r.found, 'found');
ok(r.fields.companyName === 'Acme' && r.fields.position === 'Backend Intern', 'company/title');
ok(r.fields.jobType === 'Internship' && r.fields.workMode === 'Remote', 'type/mode');
ok(r.fields.location === 'Pune, MH, IN', 'location: ' + r.fields.location);
ok(r.fields.deadline === '2030-01-02T00:00:00.000Z', 'deadline');
ok(/Build APIs/.test(r.fields.jobDescription) && /• Java/.test(r.fields.jobDescription) && !/</.test(r.fields.jobDescription), 'description stripped');
ok(JSON.stringify(r.fields.requiredSkills) === '["Java","SQL","Git"]', 'skills');

r = run(`<html><head>${ld({ '@graph': [{ '@type': 'WebSite' }, { '@type': ['JobPosting'], title: 'Dev', hiringOrganization: 'Beta Ltd', employmentType: ['FULL_TIME'] }] })}</head></html>`);
ok(r.found && r.fields.companyName === 'Beta Ltd' && r.fields.jobType === 'Full-time', '@graph and string org');
ok(r.fields.workMode === undefined && r.fields.location === undefined, 'unstated fields stay empty');

r = run('<html><head><script type="application/ld+json">{bad json</script></head></html>');
ok(!r.found, 'malformed JSON-LD ignored');

r = run(`<html><head>${ld({ '@type': 'JobPosting', title: '<img src=x onerror=alert(1)>', hiringOrganization: { name: 'X' } })}</head></html>`);
ok(r.fields.position.includes('<img'), 'title kept as plain text (popup renders with textContent)');

// LinkedIn-like DOM without JSON-LD
r = run(`<html><head><title>SWE at Contoso | LinkedIn</title></head><body>
  <div class="job-details-jobs-unified-top-card__job-title"><h1>Software Engineer</h1></div>
  <div class="job-details-jobs-unified-top-card__company-name"><a>Contoso</a></div>
  <div class="job-details-jobs-unified-top-card__primary-description-container"><span>Bengaluru, Karnataka, India</span><span>200 applicants</span></div>
  <div id="job-details">We build reliable systems.\n\nRequirements:\nJava</div>
  <button class="job-details-fit-level-preferences">Remote</button>
  <button class="job-details-fit-level-preferences">Full-time</button>
</body></html>`, 'https://www.linkedin.com/jobs/view/1234567890/?refId=abc');
ok(r.found, 'LinkedIn DOM found');
ok(r.fields.position === 'Software Engineer', 'LI title: ' + r.fields.position);
ok(r.fields.companyName === 'Contoso', 'LI company');
ok(r.fields.location === 'Bengaluru, Karnataka, India', 'LI location: ' + r.fields.location);
ok(r.fields.workMode === 'Remote' && r.fields.jobType === 'Full-time', 'LI mode/type');
ok(r.fields.applicationSource === 'LinkedIn', 'LI source');
ok(/1234567890/.test(r.url), 'LI view URL kept: ' + r.url);

// Google Careers-like
r = run(`<html><head><title>Software Engineer, Backend</title></head><body>
  <h1 data-test-id="job-title">Software Engineer, Backend</h1>
  <span data-test-id="job-location">Hyderabad, India</span>
  <div data-test-id="job-description">Design and build services used by millions.</div>
</body></html>`, 'https://careers.google.com/jobs/results/999/');
ok(r.found && r.fields.companyName === 'Google', 'Google company');
ok(r.fields.position === 'Software Engineer, Backend', 'Google title');
ok(r.fields.location === 'Hyderabad, India', 'Google location');

// OG meta fallback
r = run(`<html><head>
  <meta property="og:title" content="Platform Engineer" />
  <meta property="og:site_name" content="Example Corp" />
  <meta property="og:description" content="Ship reliable platforms." />
</head><body></body></html>`, 'https://jobs.example.com/role/1');
ok(r.found && r.fields.position === 'Platform Engineer' && r.fields.companyName === 'Example Corp', 'OG meta');

console.log(`extension extract checks: ${pass} passed, ${fail} failed`);
process.exit(fail ? 1 : 0);
