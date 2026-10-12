import { useEffect } from 'react';
import { useNavigate } from 'react-router-dom';

const isTyping = (el) => !!el && (
  el.tagName === 'INPUT' || el.tagName === 'TEXTAREA' || el.tagName === 'SELECT' || el.isContentEditable
  || !!el.closest?.('[contenteditable]:not([contenteditable="false"])')   // also nested children of an editable region
);

/** N -> Add application, / -> focus search, ? -> shortcut help. Ignored while typing or in dialogs. */
export function useShortcuts({ onHelp }) {
  const navigate = useNavigate();
  useEffect(() => {
    const handler = (e) => {
      if (e.metaKey || e.ctrlKey || e.altKey) return;
      if (isTyping(e.target) || e.target.closest?.('[role="dialog"]')) return;
      if (e.key === 'n' || e.key === 'N') { e.preventDefault(); navigate('/app/applications/new'); }
      else if (e.key === '/') {
        const el = document.querySelector('[data-search-input]');
        if (el) { e.preventDefault(); el.focus(); } else { e.preventDefault(); navigate('/app/applications?focus=search'); }
      } else if (e.key === '?') { e.preventDefault(); onHelp?.(); }
    };
    window.addEventListener('keydown', handler);
    return () => window.removeEventListener('keydown', handler);
  }, [navigate, onHelp]);
}
