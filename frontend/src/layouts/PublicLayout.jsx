import { useState } from 'react';
import { Link, Outlet, useLocation } from 'react-router-dom';
import { Menu, X } from 'lucide-react';
import { Logo } from '../components/ui/Logo';
import { Button } from '../components/ui/Button';

const LINKS = [['Features', '/#features'], ['How it works', '/#how-it-works']];

export default function PublicLayout() {
  const [open, setOpen] = useState(false);
  const { pathname } = useLocation();
  return (
    <div className="flex min-h-screen flex-col">
      <header className="sticky top-0 z-40 border-b border-border bg-bg/85 backdrop-blur">
        <div className="mx-auto flex h-16 max-w-6xl items-center justify-between px-4">
          <Link to="/" aria-label="DOCKET home"><Logo /></Link>
          <nav className="hidden items-center gap-7 md:flex" aria-label="Primary">
            {LINKS.map(([l, h]) => <a key={l} href={h} className="text-sm text-fg-muted transition-colors hover:text-fg">{l}</a>)}
          </nav>
          <div className="hidden items-center gap-2 md:flex">
            <Link to="/login"><Button variant="ghost">Log in</Button></Link>
            <Link to="/register"><Button>Create account</Button></Link>
          </div>
          <button className="rounded p-2 md:hidden" aria-label={open ? 'Close menu' : 'Open menu'} aria-expanded={open} onClick={() => setOpen((o) => !o)}>
            {open ? <X /> : <Menu />}
          </button>
        </div>
        {open && (
          <div className="border-t border-border bg-bg px-4 py-3 md:hidden">
            {LINKS.map(([l, h]) => <a key={l} href={h} onClick={() => setOpen(false)} className="block py-2 text-sm">{l}</a>)}
            <div className="mt-2 flex gap-2"><Link to="/login" className="flex-1"><Button variant="secondary" className="w-full">Log in</Button></Link><Link to="/register" className="flex-1"><Button className="w-full">Create account</Button></Link></div>
          </div>
        )}
      </header>
      <main key={pathname} className="flex-1"><Outlet /></main>
      <footer className="border-t border-border">
        <div className="mx-auto flex max-w-6xl flex-col items-start justify-between gap-6 px-4 py-10 sm:flex-row sm:items-center">
          <div><Logo size={22} /><p className="mt-2 text-sm text-fg-muted">Your career, organized.</p></div>
          <nav className="flex flex-wrap gap-x-6 gap-y-2 text-sm text-fg-muted" aria-label="Footer">
            <a href="/#features" className="hover:text-fg">Features</a><Link to="/login" className="hover:text-fg">Log in</Link>
            
          </nav>
        </div>
      </footer>
    </div>
  );
}
