import { createContext, useCallback, useContext, useRef, useState } from 'react';
import { AnimatePresence, motion } from 'motion/react';
import { CheckCircle2, AlertCircle, Info, X } from 'lucide-react';

const ToastContext = createContext(null);
export const useToast = () => useContext(ToastContext);

const ICONS = { success: CheckCircle2, error: AlertCircle, info: Info };
const TONE = { success: 'text-success', error: 'text-danger', info: 'text-info' };

export function ToastProvider({ children }) {
  const [toasts, setToasts] = useState([]);
  const id = useRef(0);
  const dismiss = useCallback((tid) => setToasts((t) => t.filter((x) => x.id !== tid)), []);
  const push = useCallback((message, { type = 'info', action, duration = 5000 } = {}) => {
    const tid = ++id.current;
    setToasts((t) => [...t, { id: tid, message, type, action }]);
    if (duration) setTimeout(() => dismiss(tid), duration);
    return tid;
  }, [dismiss]);

  const api = {
    success: (m, o) => push(m, { ...o, type: 'success' }),
    error: (m, o) => push(m, { ...o, type: 'error', duration: 7000 }),
    info: (m, o) => push(m, { ...o, type: 'info' }),
    dismiss,
  };

  return (
    <ToastContext.Provider value={api}>
      {children}
      <div className="fixed bottom-4 right-4 z-[100] flex w-[calc(100%-2rem)] max-w-sm flex-col gap-2" role="region" aria-label="Notifications" aria-live="polite">
        <AnimatePresence initial={false}>
          {toasts.map((t) => {
            const Icon = ICONS[t.type];
            return (
              <motion.div key={t.id} layout initial={{ opacity: 0, y: 12, scale: 0.98 }} animate={{ opacity: 1, y: 0, scale: 1 }} exit={{ opacity: 0, x: 24 }}
                transition={{ duration: 0.2, ease: [0.22, 1, 0.36, 1] }}
                className="card flex items-start gap-3 p-3 shadow-pop">
                <Icon size={18} className={`mt-0.5 shrink-0 ${TONE[t.type]}`} />
                <p className="flex-1 text-sm">{t.message}</p>
                {t.action && (
                  <button className="text-sm font-medium underline underline-offset-2" onClick={() => { t.action.onClick(); dismiss(t.id); }}>{t.action.label}</button>
                )}
                <button aria-label="Dismiss notification" className="text-fg-subtle hover:text-fg" onClick={() => dismiss(t.id)}><X size={16} /></button>
              </motion.div>
            );
          })}
        </AnimatePresence>
      </div>
    </ToastContext.Provider>
  );
}
