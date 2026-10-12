import { api } from './api';
export const applicationService = {
  list: (params) => api.get('/applications', { params }).then((r) => r.data),
  get: (id) => api.get(`/applications/${id}`).then((r) => r.data),
  create: (data) => api.post('/applications', data).then((r) => r.data),
  update: (id, data) => api.put(`/applications/${id}`, data).then((r) => r.data),
  remove: (id) => api.delete(`/applications/${id}`).then((r) => r.data),
  restore: (id) => api.post(`/applications/${id}/restore`).then((r) => r.data),
  setStatus: (id, status, note) => api.put(`/applications/${id}/status`, { status, note }).then((r) => r.data),
  bulkStatus: (ids, status) => api.put('/applications/bulk-status', { ids, status }).then((r) => r.data),
  history: (id) => api.get(`/applications/${id}/history`).then((r) => r.data),
  autofill: (url) => api.post('/applications/autofill', { url }).then((r) => r.data),
  checkDuplicates: (params) => api.get('/applications/duplicates', { params }).then((r) => r.data),
  suggestions: () => api.get('/applications/suggestions').then((r) => r.data),
  exportCsv: () => api.get('/applications/export', { responseType: 'blob' }),
};
