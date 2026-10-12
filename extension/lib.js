/* Shared helpers for the popup and the settings page. */
const DocketExt = (() => {
  const KEY = 'docketSettings';

  async function getSettings() {
    const r = await chrome.storage.local.get(KEY);
    return r[KEY] || { apiBase: '', token: '' };
  }
  async function saveSettings(s) { await chrome.storage.local.set({ [KEY]: s }); }

  /** Only https, or http on localhost for development. Returns the origin without a trailing slash, or null. */
  function normalizeBase(input) {
    let u;
    try { u = new URL(String(input || '').trim()); } catch (e) { return null; }
    const local = u.hostname === 'localhost' || u.hostname === '127.0.0.1';
    if (u.protocol !== 'https:' && !(u.protocol === 'http:' && local)) return null;
    if (u.username || u.password) return null;
    return u.origin;
  }

  class ApiError extends Error {
    constructor(message, status, fieldErrors) { super(message); this.status = status; this.fieldErrors = fieldErrors || {}; }
  }

  async function call(settings, method, path, body) {
    let res;
    try {
      res = await fetch(settings.apiBase + path, {
        method, credentials: 'omit', cache: 'no-store',
        headers: { Authorization: 'Bearer ' + settings.token, Accept: 'application/json', ...(body ? { 'Content-Type': 'application/json' } : {}) },
        body: body ? JSON.stringify(body) : undefined,
      });
    } catch (e) {
      throw new ApiError('Could not reach DOCKET. Check the address in Settings and your connection.', 0);
    }
    let data = null;
    try { data = await res.json(); } catch (e) { /* no body */ }
    if (!res.ok) {
      let msg;
      if (res.status === 401) {
        msg = 'Your extension token is missing, expired or revoked. Create a new one in DOCKET Settings → Extension and paste it here.';
      } else if (res.status === 403) {
        msg = (data && data.message) || 'DOCKET refused this request (403). Check that the token is still valid, the server allows extension origins, and you are saving via the extension API — not the website session.';
      } else if (res.status === 429) {
        msg = (data && data.message) || 'Too many requests. Wait a minute and try again.';
      } else {
        msg = (data && data.message) || 'DOCKET could not complete the request (error ' + res.status + ').';
      }
      throw new ApiError(msg, res.status, data && data.fieldErrors);
    }
    return data;
  }

  const api = {
    me: (s) => call(s, 'GET', '/api/extension/me'),
    duplicates: (s, company, position, url) => call(s, 'GET', '/api/extension/duplicates?' + new URLSearchParams({ company: company || '', position: position || '', url: url || '' })),
    create: (s, body) => call(s, 'POST', '/api/extension/applications', body),
  };

  return { getSettings, saveSettings, normalizeBase, api, ApiError };
})();
if (typeof globalThis !== 'undefined') globalThis.DocketExt = DocketExt;
