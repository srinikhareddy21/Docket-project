import { useCallback, useEffect, useRef, useState } from 'react';
import { apiError } from '../services/api';

/** Fetches data on mount / dependency change with loading + error state. */
export function useAsync(fn, deps = []) {
  const [state, setState] = useState({ data: null, loading: true, error: null });
  const alive = useRef(true);
  const run = useCallback(async () => {
    setState((s) => ({ ...s, loading: true, error: null }));
    try { const data = await fn(); if (alive.current) setState({ data, loading: false, error: null }); }
    catch (e) { if (alive.current) setState({ data: null, loading: false, error: apiError(e) }); }
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, deps);
  useEffect(() => { alive.current = true; run(); return () => { alive.current = false; }; }, [run]);
  const setData = useCallback((data) => setState({ data, loading: false, error: null }), []);
  return { ...state, reload: run, setData };
}
