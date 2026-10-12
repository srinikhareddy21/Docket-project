import { motion } from 'motion/react';
import { AlertTriangle, RefreshCw } from 'lucide-react';
import { STATUS_TOKEN } from '../../config/constants';
import { Button } from './Button';

export function StatusBadge({ status }) {
  const t = STATUS_TOKEN[status] || 'status-applied';
  return (
    <span className="inline-flex items-center gap-1.5 rounded-full px-2.5 py-0.5 text-xs font-medium"
      style={{ color: `rgb(var(--${t}))`, backgroundColor: `rgb(var(--${t}) / 0.12)` }}>
      <span className="h-1.5 w-1.5 rounded-full" style={{ backgroundColor: `rgb(var(--${t}))` }} />{status}
    </span>
  );
}

export function PriorityBadge({ level }) {
  const tone = { High: 'text-danger bg-danger/10', Medium: 'text-warning bg-warning/10', Low: 'text-fg-muted bg-surface-muted' }[level] || '';
  return <span className={`rounded-full px-2.5 py-0.5 text-xs font-medium ${tone}`}>{level}</span>;
}

export function ProgressBar({ value, label, className = '' }) {
  const v = Math.max(0, Math.min(100, value || 0));
  return (
    <div className={className}>
      <div className="h-2 overflow-hidden rounded-full bg-surface-muted" role="progressbar" aria-valuenow={Math.round(v)} aria-valuemin={0} aria-valuemax={100} aria-label={label}>
        <motion.div className="h-full rounded-full bg-primary" initial={{ width: 0 }} animate={{ width: `${v}%` }} transition={{ duration: 0.6, ease: [0.22, 1, 0.36, 1] }} />
      </div>
    </div>
  );
}

export function Skeleton({ className = '' }) { return <div className={`skeleton ${className}`} aria-hidden="true" />; }
export function SkeletonRows({ rows = 5 }) {
  return <div className="space-y-3" role="status" aria-label="Loading">{Array.from({ length: rows }, (_, i) => <Skeleton key={i} className="h-12 w-full" />)}</div>;
}

export function EmptyState({ icon: Icon, title, description, actions }) {
  return (
    <div className="card flex flex-col items-center px-6 py-14 text-center">
      {Icon && <div className="mb-4 flex h-12 w-12 items-center justify-center rounded-full bg-primary-soft text-primary"><Icon size={22} /></div>}
      <h3 className="text-lg font-semibold">{title}</h3>
      {description && <p className="mt-1.5 max-w-md text-sm text-fg-muted">{description}</p>}
      {actions && <div className="mt-5 flex flex-wrap justify-center gap-2">{actions}</div>}
    </div>
  );
}

export function ErrorState({ error, onRetry }) {
  return (
    <div className="card flex items-start gap-3 p-5" role="alert">
      <AlertTriangle className="mt-0.5 shrink-0 text-danger" size={20} />
      <div className="flex-1">
        <p className="font-medium">We could not load this</p>
        <p className="mt-0.5 text-sm text-fg-muted">{error?.message || 'Try again in a moment.'}</p>
      </div>
      {onRetry && <Button size="sm" variant="secondary" icon={RefreshCw} onClick={onRetry}>Retry</Button>}
    </div>
  );
}

/** Honest placeholder for features whose backend/integration is not built yet. */
export function UnavailableNotice({ title = 'Not available yet', children }) {
  return (
    <div className="rounded-card border border-dashed border-border-strong bg-surface-muted/60 p-4 text-sm" role="note">
      <p className="font-medium">{title}</p>
      <p className="mt-0.5 text-fg-muted">{children}</p>
    </div>
  );
}

export function PageHeader({ title, description, actions }) {
  return (
    <div className="mb-6 flex flex-wrap items-end justify-between gap-3">
      <div>
        <h1 className="text-2xl font-semibold sm:text-3xl">{title}</h1>
        {description && <p className="mt-1 max-w-2xl text-sm text-fg-muted">{description}</p>}
      </div>
      {actions && <div className="flex flex-wrap gap-2">{actions}</div>}
    </div>
  );
}

export function Card({ title, action, children, className = '' }) {
  return (
    <section className={`card p-5 ${className}`}>
      {(title || action) && <div className="mb-4 flex items-center justify-between gap-2">{title && <h2 className="text-base font-semibold">{title}</h2>}{action}</div>}
      {children}
    </section>
  );
}
