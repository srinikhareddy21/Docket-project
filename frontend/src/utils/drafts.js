/** Local draft helpers. Drafts are NOT persisted records; the UI labels them as unsaved. */
const key = (k) => `docket.draft.${k}`;
export const loadDraft = (k) => { try { const v = localStorage.getItem(key(k)); return v ? JSON.parse(v) : null; } catch { return null; } };
export const saveDraft = (k, v) => { try { localStorage.setItem(key(k), JSON.stringify(v)); } catch { /* storage unavailable */ } };
export const clearDraft = (k) => { try { localStorage.removeItem(key(k)); } catch { /* noop */ } };
export const clearAllDrafts = () => { try { Object.keys(localStorage).filter((x) => x.startsWith('docket.draft.')).forEach((x) => localStorage.removeItem(x)); } catch { /* noop */ } };
