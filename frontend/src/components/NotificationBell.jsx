import { useEffect, useRef, useState } from 'react';
import { Link } from 'react-router-dom';
import { Bell } from 'lucide-react';
import { notificationService } from '../services/workspace';
import { apiError } from '../services/api';
import { formatDateTime } from '../utils/format';

/**
 * In-app notification bell. Unread count and list come from the server; nothing is invented client-side.
 * Separate from optional email reminders.
 */
export default function NotificationBell() {
  const [open, setOpen] = useState(false);
  const [feed, setFeed] = useState({ items: [], unreadCount: 0 });
  const [loading, setLoading] = useState(false);
  const root = useRef(null);

  const load = async () => {
    setLoading(true);
    try {
      const data = await notificationService.list();
      setFeed({ items: data.items || [], unreadCount: data.unreadCount || 0 });
    } catch {
      /* keep last good state */
    } finally {
      setLoading(false);
    }
  };

  useEffect(() => {
    load();
    const t = setInterval(load, 60_000);
    return () => clearInterval(t);
  }, []);

  useEffect(() => {
    if (!open) return;
    const onDoc = (e) => { if (root.current && !root.current.contains(e.target)) setOpen(false); };
    const onKey = (e) => { if (e.key === 'Escape') setOpen(false); };
    document.addEventListener('mousedown', onDoc);
    document.addEventListener('keydown', onKey);
    return () => { document.removeEventListener('mousedown', onDoc); document.removeEventListener('keydown', onKey); };
  }, [open]);

  const toggle = async () => {
    const next = !open;
    setOpen(next);
    if (next) await load();
  };

  const markRead = async (id) => {
    try {
      await notificationService.markRead(id);
      setFeed((f) => ({
        items: f.items.map((n) => (n.id === id ? { ...n, read: true } : n)),
        unreadCount: Math.max(0, f.unreadCount - (f.items.find((n) => n.id === id && !n.read) ? 1 : 0)),
      }));
    } catch (e) {
      /* ignore */
    }
  };

  const markAll = async () => {
    try {
      await notificationService.markAllRead();
      setFeed((f) => ({ items: f.items.map((n) => ({ ...n, read: true })), unreadCount: 0 }));
    } catch (e) {
      /* ignore */
    }
  };

  return (
    <div className="relative" ref={root}>
      <button
        type="button"
        className="relative rounded p-2 text-fg-muted hover:bg-surface-muted hover:text-fg"
        aria-label={feed.unreadCount ? `${feed.unreadCount} unread notifications` : 'Notifications'}
        aria-expanded={open}
        aria-haspopup="true"
        onClick={toggle}
      >
        <Bell size={18} />
        {feed.unreadCount > 0 && (
          <span className="absolute right-1 top-1 flex h-4 min-w-4 items-center justify-center rounded-full bg-danger px-1 text-[10px] font-semibold text-white">
            {feed.unreadCount > 99 ? '99+' : feed.unreadCount}
          </span>
        )}
      </button>
      {open && (
        <div
          role="menu"
          className="absolute right-0 z-40 mt-2 w-[min(100vw-2rem,22rem)] overflow-hidden rounded-card border border-border bg-surface shadow-elevated"
        >
          <div className="flex items-center justify-between border-b border-border px-3 py-2">
            <p className="text-sm font-medium">Notifications</p>
            {feed.unreadCount > 0 && (
              <button type="button" className="text-xs text-primary hover:underline" onClick={markAll}>
                Mark all read
              </button>
            )}
          </div>
          <ul className="max-h-80 overflow-y-auto">
            {loading && !feed.items.length && <li className="px-3 py-4 text-sm text-fg-muted">Loading…</li>}
            {!loading && feed.items.length === 0 && (
              <li className="px-3 py-6 text-center text-sm text-fg-muted">
                No notifications yet. Upcoming deadlines, interviews and follow-ups appear here.
              </li>
            )}
            {feed.items.map((n) => (
              <li key={n.id} className={`border-b border-border last:border-0 ${n.read ? 'bg-surface' : 'bg-primary/5'}`}>
                <div className="flex gap-2 px-3 py-2.5">
                  <div className="min-w-0 flex-1">
                    {n.href ? (
                      <Link
                        to={n.href}
                        className="block text-sm font-medium hover:underline"
                        onClick={() => { if (!n.read) markRead(n.id); setOpen(false); }}
                      >
                        {n.title}
                      </Link>
                    ) : (
                      <p className="text-sm font-medium">{n.title}</p>
                    )}
                    {n.body && <p className="mt-0.5 text-xs text-fg-muted">{n.body}</p>}
                    <p className="mt-1 text-[11px] text-fg-subtle">{formatDateTime(n.createdAt)}</p>
                  </div>
                  {!n.read && (
                    <button
                      type="button"
                      className="shrink-0 self-start text-[11px] text-primary hover:underline"
                      onClick={() => markRead(n.id)}
                    >
                      Mark read
                    </button>
                  )}
                </div>
              </li>
            ))}
          </ul>
        </div>
      )}
    </div>
  );
}
