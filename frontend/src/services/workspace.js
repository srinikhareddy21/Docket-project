import { api } from './api';
const get = (u, p) => api.get(u, { params: p }).then((r) => r.data);
const post = (u, d) => api.post(u, d).then((r) => r.data);
const put = (u, d) => api.put(u, d).then((r) => r.data);
const del = (u) => api.delete(u).then((r) => r.data);

export const profileService = {
  get: () => get('/profile'), update: (d) => put('/profile', d),
  changePassword: (d) => post('/profile/password', d),
  requestEmailChange: (email) => post('/profile/email-change', { email }),
  requestDeletion: (password) => post('/profile/delete-request', { password }),
};
export const skillService = {
  list: () => get('/skills'), add: (d) => post('/skills', d), remove: (id) => del(`/skills/${id}`),
};
export const interviewService = {
  list: (p) => get('/interviews', p),
  create: (appId, d) => post(`/applications/${appId}/interviews`, d),
  update: (id, d) => put(`/interviews/${id}`, d),
  remove: (id) => del(`/interviews/${id}`),
};
export const checklistService = {
  list: (appId) => get(`/applications/${appId}/checklist`),
  add: (appId, d) => post(`/applications/${appId}/checklist`, d),
  toggle: (id, completed) => api.patch(`/checklist/${id}`, { completed }).then((r) => r.data),
  update: (id, d) => api.patch(`/checklist/${id}`, d).then((r) => r.data),
  remove: (id) => del(`/checklist/${id}`),
};
export const documentService = {
  list: (appId) => get(`/applications/${appId}/documents`),
  upload: (appId, formData) => api.post(`/applications/${appId}/documents`, formData).then((r) => r.data),
  remove: (id) => del(`/documents/${id}`),
  download: (id) => api.get(`/documents/${id}/download`, { responseType: 'blob' }),
};
export const dashboardService = {
  summary: () => get('/dashboard'),
  funnel: () => get('/analytics/funnel'),
  sources: () => get('/analytics/sources'),
  trends: () => get('/analytics/trends'),
  actions: () => get('/dashboard/actions'),
  exportAnalysisCsv: () => api.get('/analytics/export', { responseType: 'blob' }),
};
export const reminderService = {
  list: () => get('/reminders'), create: (d) => post('/reminders', d), update: (id, d) => put(`/reminders/${id}`, d),
  getPrefs: () => get('/reminders/preferences'), savePrefs: (d) => put('/reminders/preferences', d),
};
export const goalService = {
  current: () => get('/goals'), create: (d) => post('/goals', d), update: (id, d) => put(`/goals/${id}`, d),
};
export const shareService = {
  list: () => get('/share-links'), create: (d) => post('/share-links', d), revoke: (id) => del(`/share-links/${id}`),
  publicView: (token) => get(`/share/${encodeURIComponent(token)}`),
};
export const calendarService = {
  status: () => get('/integrations/google-calendar'),
  connect: () => post('/integrations/google-calendar/connect'),
  savePrefs: (d) => put('/integrations/google-calendar/preferences', d),
  sync: () => post('/integrations/google-calendar/sync'),
  disconnect: (removeEvents) => api.delete('/integrations/google-calendar', { params: { removeEvents } }).then((r) => r.data),
};
export const extensionService = {
  list: () => get('/extension-tokens'),
  create: (label) => post('/extension-tokens', { label }),
  revoke: (id) => del(`/extension-tokens/${id}`),
};

export const notificationService = {
  list: () => get('/notifications'),
  markRead: (id) => post(`/notifications/${id}/read`),
  markAllRead: () => post('/notifications/read-all'),
};
