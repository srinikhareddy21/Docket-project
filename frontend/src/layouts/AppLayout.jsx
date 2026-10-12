import { useCallback, useState } from 'react';
import { NavLink, Outlet, useLocation, useNavigate, Link } from 'react-router-dom';
import { AnimatePresence, motion } from 'motion/react';
import { Menu, Search, LogOut, Plus, Keyboard, LayoutDashboard, Briefcase, Columns3, PlusCircle, CalendarClock, Bell, BarChart3, Sparkles, Target, Mail, Share2, User, Settings } from 'lucide-react';

const Icons = { LayoutDashboard, Briefcase, Columns3, PlusCircle, CalendarClock, Bell, BarChart3, Sparkles, Target, Mail, Share2, User, Settings };
import { NAV } from '../config/constants';
import { Logo } from '../components/ui/Logo';
import { Button } from '../components/ui/Button';
import NotificationBell from '../components/NotificationBell';
import { Modal } from '../components/ui/Modal';
import { useAuth } from '../context/AuthContext';
import { useShortcuts } from '../hooks/useShortcuts';

function SidebarNav({ onNavigate }) {
  return (
    <nav className="flex-1 space-y-0.5 overflow-y-auto px-3 py-2" aria-label="Workspace">
      {NAV.map(({ to, label, icon, end }) => {
        const Icon = Icons[icon];
        return (
          <NavLink key={to} to={to} end={end} onClick={onNavigate}
            className={({ isActive }) => `relative flex items-center gap-3 rounded-control px-3 py-2 text-sm transition-colors ${isActive ? 'font-medium text-fg' : 'text-fg-muted hover:bg-surface-muted hover:text-fg'}`}>
            {({ isActive }) => (<>
              {isActive && <motion.span layoutId="nav-active" className="absolute inset-0 rounded-control bg-primary-soft" transition={{ duration: 0.22, ease: [0.22, 1, 0.36, 1] }} />}
              <Icon size={17} className="relative" /><span className="relative">{label}</span>
            </>)}
          </NavLink>
        );
      })}
    </nav>
  );
}

export default function AppLayout() {
  const { user, logout } = useAuth();
  const navigate = useNavigate();
  const { pathname } = useLocation();
  const [drawer, setDrawer] = useState(false);
  const [help, setHelp] = useState(false);
  const [q, setQ] = useState('');
  const openHelp = useCallback(() => setHelp(true), []);
  useShortcuts({ onHelp: openHelp });

  const submitSearch = (e) => { e.preventDefault(); navigate(`/app/applications${q.trim() ? `?q=${encodeURIComponent(q.trim())}` : ''}`); };

  return (
    <div className="flex min-h-screen">
      <a href="#main-content" className="sr-only focus:not-sr-only focus:fixed focus:left-3 focus:top-3 focus:z-[60] focus:rounded-control focus:bg-surface focus:px-3 focus:py-2 focus:text-sm focus:shadow-pop">Skip to main content</a>
      <aside className="sticky top-0 hidden h-screen w-64 shrink-0 flex-col border-r border-border bg-surface lg:flex">
        <div className="flex h-16 items-center px-5"><Link to="/app" aria-label="DOCKET dashboard"><Logo /></Link></div>
        <SidebarNav />
        <div className="border-t border-border p-3">
          <div className="mb-2 truncate px-2 text-sm"><p className="truncate font-medium">{user?.name}</p><p className="truncate text-xs text-fg-subtle">{user?.email}</p></div>
          <Button variant="ghost" size="sm" icon={LogOut} className="w-full justify-start" onClick={async () => { await logout(); navigate('/login'); }}>Log out</Button>
        </div>
      </aside>

      <AnimatePresence>
        {drawer && (
          <div className="fixed inset-0 z-50 lg:hidden">
            <motion.div className="absolute inset-0 bg-fg/40" initial={{ opacity: 0 }} animate={{ opacity: 1 }} exit={{ opacity: 0 }} onClick={() => setDrawer(false)} />
            <motion.aside className="absolute inset-y-0 left-0 flex w-72 flex-col bg-surface shadow-pop" initial={{ x: -288 }} animate={{ x: 0 }} exit={{ x: -288 }} transition={{ duration: 0.22, ease: [0.22, 1, 0.36, 1] }}>
              <div className="flex h-16 items-center px-5"><Logo /></div>
              <SidebarNav onNavigate={() => setDrawer(false)} />
              <div className="border-t border-border p-3"><Button variant="ghost" size="sm" icon={LogOut} className="w-full justify-start" onClick={async () => { await logout(); navigate('/login'); }}>Log out</Button></div>
            </motion.aside>
          </div>
        )}
      </AnimatePresence>

      <div className="flex min-w-0 flex-1 flex-col">
        <header className="sticky top-0 z-30 flex h-16 items-center gap-3 border-b border-border bg-bg/90 px-4 backdrop-blur">
          <button className="rounded p-2 lg:hidden" aria-label="Open navigation" onClick={() => setDrawer(true)}><Menu size={20} /></button>
          <form onSubmit={submitSearch} role="search" className="relative max-w-md flex-1">
            <Search size={16} className="absolute left-3 top-1/2 -translate-y-1/2 text-fg-subtle" />
            <input data-search-input value={q} onChange={(e) => setQ(e.target.value)} className="field pl-9 pr-9" placeholder="Search applications" aria-label="Search applications" />
            <kbd className="pointer-events-none absolute right-2.5 top-1/2 hidden -translate-y-1/2 rounded border border-border px-1.5 text-xs text-fg-subtle sm:block">/</kbd>
          </form>
          <div className="ml-auto flex items-center gap-2">
            <NotificationBell />
            <button className="hidden rounded p-2 text-fg-muted hover:bg-surface-muted sm:block" aria-label="Keyboard shortcuts" onClick={openHelp}><Keyboard size={18} /></button>
            <Link to="/app/applications/new"><Button size="sm" icon={Plus}>Add application</Button></Link>
          </div>
        </header>
        <main id="main-content" tabIndex={-1} className="mx-auto w-full max-w-6xl flex-1 px-4 py-8 outline-none">
          <motion.div key={pathname} initial={{ opacity: 0, y: 6 }} animate={{ opacity: 1, y: 0 }} transition={{ duration: 0.18, ease: [0.22, 1, 0.36, 1] }}>
            <Outlet />
          </motion.div>
        </main>
      </div>

      <Modal open={help} onClose={() => setHelp(false)} title="Keyboard shortcuts" size="sm">
        <dl className="space-y-3 text-sm">
          {[['N', 'Add application'], ['/', 'Search applications'], ['?', 'Show this list'], ['Esc', 'Close dialogs']].map(([k, d]) => (
            <div key={k} className="flex items-center justify-between"><dt className="text-fg-muted">{d}</dt><dd><kbd className="rounded border border-border-strong bg-surface-muted px-2 py-0.5 text-xs">{k}</kbd></dd></div>
          ))}
        </dl>
        <p className="mt-4 text-xs text-fg-subtle">Shortcuts are paused while you type in a field or have a dialog open.</p>
      </Modal>
    </div>
  );
}
