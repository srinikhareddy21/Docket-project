import { createContext, useCallback, useContext, useEffect, useMemo, useState } from 'react';
import { authService } from '../services/auth';
import { apiError, setUnauthorizedHandler } from '../services/api';
import { clearAllDrafts } from '../utils/drafts';

const AuthContext = createContext(null);
export const useAuth = () => useContext(AuthContext);

/**
 * Auth state mirrors the server session (HttpOnly cookies). `status`:
 *  loading | authenticated | anonymous | unreachable
 * No user is ever assumed; the backend must confirm via /auth/me.
 */
export function AuthProvider({ children }) {
  const [user, setUser] = useState(null);
  const [status, setStatus] = useState('loading');

  const restore = useCallback(async () => {
    setStatus('loading');
    try {
      let u;
      try { u = await authService.me(); }
      catch (e) {
        if (apiError(e).status !== 401) throw e;
        await authService.refresh();   // access cookie expired but refresh cookie may still be valid
        u = await authService.me();
      }
      setUser(u); setStatus('authenticated');
    }
    catch (e) { setUser(null); setStatus(apiError(e).network ? 'unreachable' : 'anonymous'); }
  }, []);

  useEffect(() => { restore(); }, [restore]);
  useEffect(() => { setUnauthorizedHandler(() => { setUser(null); setStatus('anonymous'); }); }, []);

  const login = useCallback(async (creds) => { const u = await authService.login(creds); setUser(u); setStatus('authenticated'); return u; }, []);
  const register = useCallback(async (data) => authService.register(data), []);
  const logout = useCallback(async () => {
    try { await authService.logout(); } catch { /* cookie may already be invalid */ }
    clearAllDrafts(); setUser(null); setStatus('anonymous');
  }, []);

  const value = useMemo(() => ({ user, setUser, status, login, register, logout, restore }), [user, status, login, register, logout, restore]);
  return <AuthContext.Provider value={value}>{children}</AuthContext.Provider>;
}
