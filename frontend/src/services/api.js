import axios from 'axios';

/**
 * Central Axios instance. Credentials travel in HttpOnly cookies set by the backend,
 * so the frontend never reads or stores a JWT.
 */
export const api = axios.create({
  baseURL: import.meta.env.VITE_API_BASE_URL || '/api',
  withCredentials: true,
  // Spring sets a readable XSRF-TOKEN cookie; echo it back in X-XSRF-TOKEN on every request.
  withXSRFToken: true,
  timeout: 20000,
});

let onUnauthorized = () => {};
export const setUnauthorizedHandler = (fn) => { onUnauthorized = fn; };

let refreshing = null;
api.interceptors.response.use(
  (r) => r,
  async (error) => {
    const { config, response } = error;
    const isAuthCall = config?.url?.startsWith('/auth/');
    // First state-changing request before the CSRF cookie existed: fetch it once and retry.
    if (response?.status === 403 && response.data?.code === 'CSRF_INVALID' && config && !config._csrfRetried) {
      config._csrfRetried = true;
      await api.get('/auth/csrf');
      return api(config);
    }
    if (response?.status === 401 && config && !config._retried && !isAuthCall) {
      config._retried = true;
      try {
        refreshing = refreshing || api.post('/auth/refresh').finally(() => { refreshing = null; });
        await refreshing;
        return api(config);
      } catch {
        onUnauthorized();
      }
    }
    return Promise.reject(error);
  },
);

/** Normalizes any failure into { message, status, fieldErrors, network }. */
export function apiError(err) {
  if (err?.response) {
    const d = err.response.data || {};
    const fallback = {
      400: 'Some details are invalid. Check the form and try again.',
      401: 'Your session has ended. Log in again.',
      403: 'You do not have access to this.',
      404: 'We could not find what you asked for.',
      409: 'This conflicts with an existing record.',
      429: 'Too many attempts. Wait a moment and try again.',
    }[err.response.status] || 'Something went wrong on our side. Try again shortly.';
    return { message: d.message || fallback, status: err.response.status, fieldErrors: d.fieldErrors || {}, network: false };
  }
  return { message: 'Cannot reach the DOCKET server. Check that the backend is running.', status: 0, fieldErrors: {}, network: true };
}
