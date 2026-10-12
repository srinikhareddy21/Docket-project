(async () => {
  const $ = (id) => document.getElementById(id);
  const status = (msg, cls) => { $('status').textContent = msg; $('status').className = 'small ' + (cls || ''); };
  const s = await DocketExt.getSettings();
  $('base').value = s.apiBase || '';
  $('token').value = s.token || '';

  $('form').addEventListener('submit', async (e) => {
    e.preventDefault();
    const base = DocketExt.normalizeBase($('base').value);
    const token = $('token').value.trim();
    if (!base) return status('Enter a valid address: https://… (or http://localhost:8080 for development).', 'err');
    if (!token.startsWith('dkx_')) return status('That does not look like a DOCKET extension token (it starts with dkx_).', 'err');
    $('save').disabled = true;
    try {
      // Needs a user gesture: asks Chrome to let this extension talk to that one server.
      const granted = await chrome.permissions.request({ origins: [base + '/*'] });
      if (!granted) return status('Permission to contact that server was not granted, so the extension cannot save jobs.', 'err');
      const me = await DocketExt.api.me({ apiBase: base, token });
      await DocketExt.saveSettings({ apiBase: base, token });
      status('Connected as ' + me.name + '. You can now save jobs from any job page.', 'ok');
    } catch (err) {
      status(err.message || 'Could not connect.', 'err');
    } finally { $('save').disabled = false; }
  });
})();
