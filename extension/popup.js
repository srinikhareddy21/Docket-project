(async () => {
  const app = document.getElementById('app');
  // Everything from web pages is put into the DOM with textContent / .value only, never as HTML.
  const el = (tag, props = {}, ...kids) => {
    const n = document.createElement(tag);
    for (const [k, v] of Object.entries(props)) { if (k === 'class') n.className = v; else if (k === 'text') n.textContent = v; else n.setAttribute(k, v); }
    kids.forEach((c) => n.append(c));
    return n;
  };
  const show = (...nodes) => app.replaceChildren(...nodes);
  const message = (text, cls = 'muted') => show(el('p', { class: cls, text }));
  const field = (id, label, control) => { control.id = id; return el('div', {}, el('label', { for: id, text: label }), control); };
  const select = (opts, value) => { const s = el('select'); opts.forEach(([v, t]) => s.append(Object.assign(el('option', { text: t }), { value: v }))); s.value = value || ''; return s; };
  const orNull = (v) => { const t = (v || '').trim(); return t ? t : null; };

  const settings = await DocketExt.getSettings();
  if (!settings.apiBase || !settings.token) {
    const b = el('button', { type: 'button', text: 'Open settings' });
    b.onclick = () => chrome.runtime.openOptionsPage();
    return show(el('p', { text: 'Connect the extension to your DOCKET account first.' }), b);
  }
  if (!(await chrome.permissions.contains({ origins: [settings.apiBase + '/*'] }))) {
    const b = el('button', { type: 'button', text: 'Open settings' });
    b.onclick = () => chrome.runtime.openOptionsPage();
    return show(el('p', { text: 'The extension no longer has permission to contact your DOCKET server. Open settings and save again.' }), b);
  }

  const [tab] = await chrome.tabs.query({ active: true, currentWindow: true });
  if (!tab || !/^https?:\/\//i.test(tab.url || '')) return message('This kind of page cannot be read. Open a job posting in a normal web page and try again.');

  let extracted = { found: false, fields: {} };
  let readNote = '';
  try {
    const [r] = await chrome.scripting.executeScript({ target: { tabId: tab.id }, func: docketExtract });
    if (r && r.result) extracted = r.result;
  } catch (e) {
    readNote = 'This page does not allow the extension to read it. Fill in the details yourself.';
  }
  const f = extracted.fields || {};
  if (!readNote) readNote = extracted.found
    ? 'Details below came from the job data this page publishes. Check each one before saving.'
    : 'This page does not publish job details the extension can read. Nothing was filled in. Enter them yourself.';

  const company = el('input', { maxlength: 150, required: '' }); company.value = f.companyName || '';
  const position = el('input', { maxlength: 150, required: '' }); position.value = f.position || '';
  const location_ = el('input', { maxlength: 150 }); location_.value = f.location || '';
  const preferredUrl = (extracted.url && extracted.url.length <= 2048) ? extracted.url : (tab.url.length <= 2048 ? tab.url : '');
  const link = el('input', { maxlength: 2048, type: 'url' }); link.value = preferredUrl;
  const jobType = select([['', '—'], ['Internship', 'Internship'], ['Full-time', 'Full-time'], ['Part-time', 'Part-time'], ['Other', 'Other']], f.jobType);
  const workMode = select([['', '—'], ['Remote', 'Remote'], ['Hybrid', 'Hybrid'], ['Onsite', 'Onsite']], f.workMode);
  const status = select(['Applied', 'Shortlisted', 'Assessment', 'Interview', 'Selected', 'Rejected', 'Withdrawn'].map((x) => [x, x]), 'Applied');
  const source = select([['', '—'], ['Referral', 'Referral'], ['LinkedIn', 'LinkedIn'], ['Other job portal', 'Other job portal'], ['Company career page', 'Company career page'], ['College placement cell', 'College placement cell'], ['Other', 'Other']], f.applicationSource || '');
  const appDate = el('input', { type: 'date' });
  const skills = el('input'); skills.value = (f.requiredSkills || []).join(', ');
  const incDesc = el('input', { type: 'checkbox' }); incDesc.checked = !!f.jobDescription;
  const out = el('p', { class: 'small', role: 'status' });
  const dupBox = el('div');
  const save = el('button', { type: 'submit', text: 'Save to DOCKET' });

  const form = el('form', { novalidate: '' },
    el('p', { class: 'note', text: readNote }),
    dupBox,
    field('c', 'Company', company), field('p', 'Job title', position), field('l', 'Location', location_),
    el('div', { class: 'row' }, field('jt', 'Job type', jobType), field('wm', 'Work mode', workMode)),
    field('ln', 'Job link', link),
    el('div', { class: 'row' }, field('st', 'Status', status), field('so', 'Source', source)),
    field('ad', 'Application date (leave empty if you have not applied yet)', appDate),
    field('sk', 'Required skills (comma separated)', skills));
  if (f.jobDescription) form.append(el('label', {}, incDesc, ` Save the job description (${f.jobDescription.length.toLocaleString()} characters)`));
  if (f.deadline) form.append(el('p', { class: 'small muted', text: 'Deadline found on the page: ' + new Date(f.deadline).toLocaleString() }));
  form.append(save, out);
  show(form);

  let dupKey = '';
  const checkDup = async () => {
    const key = [company.value, position.value, link.value].join('|');
    if (key === dupKey) return; dupKey = key;
    if (!company.value.trim() && !link.value.trim()) return dupBox.replaceChildren();
    try {
      const d = await DocketExt.api.duplicates(settings, company.value, position.value, link.value);
      dupBox.replaceChildren(...(d.length ? [el('p', { class: 'note', text: 'Possible duplicate in DOCKET: ' + d.map((x) => `${x.companyName} — ${x.position} (${x.currentStatus})`).join('; ') + '. You can still save.' })] : []));
      save.textContent = d.length ? 'Save anyway' : 'Save to DOCKET';
    } catch (e) { out.className = 'small err'; out.textContent = e.message; }
  };
  [company, position, link].forEach((i) => i.addEventListener('change', checkDup));
  checkDup();

  form.addEventListener('submit', async (ev) => {
    ev.preventDefault();
    out.className = 'small'; out.textContent = '';
    if (!company.value.trim() || !position.value.trim()) { out.className = 'small err'; out.textContent = 'Company and job title are required.'; return; }
    const body = {
      companyName: company.value.trim(), position: position.value.trim(), jobType: orNull(jobType.value), workMode: orNull(workMode.value),
      location: orNull(location_.value), jobDescription: incDesc.checked ? orNull(f.jobDescription) : null,
      requiredSkills: skills.value.split(',').map((x) => x.trim()).filter(Boolean).slice(0, 50),
      applicationSource: orNull(source.value), applicationDate: orNull(appDate.value), deadline: f.deadline || null,
      applicationLink: orNull(link.value), currentStatus: status.value,
    };
    save.disabled = true; save.textContent = 'Saving…';
    try {
      await DocketExt.api.create(settings, body); // success only after DOCKET answers 201
      show(el('p', { class: 'ok', role: 'status', text: 'Saved to DOCKET.' }), el('p', { class: 'small muted', text: 'Open DOCKET to see it in your applications.' }));
    } catch (e) {
      const fe = Object.values(e.fieldErrors || {});
      out.className = 'small err'; out.textContent = fe.length ? fe.join(' ') : e.message;
      save.disabled = false; save.textContent = 'Try again';
    }
  });
})();
