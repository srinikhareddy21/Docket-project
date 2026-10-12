import { Link } from 'react-router-dom';
import { ExternalLink } from 'lucide-react';
import { STATUSES } from '../config/constants';
import { PriorityBadge, StatusBadge } from './ui/Display';
import { formatDate, isOverdue } from '../utils/format';

export default function AppTable({ rows, selected, onToggle, onToggleAll, onStatus, busyId }) {
  const all = rows.length > 0 && rows.every((r) => selected.has(r.id));
  return (
    <div className="card overflow-x-auto">
      <table className="w-full min-w-[820px] text-left text-sm">
        <thead className="border-b border-border bg-surface-muted/50 text-xs text-fg-muted">
          <tr>
            <th className="w-10 px-3 py-3"><input type="checkbox" aria-label="Select all on this page" checked={all} onChange={onToggleAll} className="accent-[rgb(var(--color-primary))]" /></th>
            {['Company', 'Role', 'Status', 'Applied', 'Deadline', 'Match', 'Priority', 'Next action'].map((h) => <th key={h} className="px-3 py-3 font-medium">{h}</th>)}
          </tr>
        </thead>
        <tbody className="divide-y divide-border">
          {rows.map((r) => (
            <tr key={r.id} className={`transition-colors hover:bg-surface-muted/40 ${selected.has(r.id) ? 'bg-primary-soft/50' : ''}`}>
              <td className="px-3 py-3"><input type="checkbox" aria-label={`Select ${r.companyName}`} checked={selected.has(r.id)} onChange={() => onToggle(r.id)} className="accent-[rgb(var(--color-primary))]" /></td>
              <td className="max-w-[180px] px-3 py-3"><Link to={`/app/applications/${r.id}`} className="block truncate font-medium hover:underline">{r.companyName}</Link></td>
              <td className="max-w-[200px] px-3 py-3"><span className="flex items-center gap-1.5"><span className="truncate">{r.position}</span>{r.applicationLink && <a href={r.applicationLink} target="_blank" rel="noopener noreferrer" aria-label={`Open original posting for ${r.companyName}`} className="text-fg-subtle hover:text-fg"><ExternalLink size={13} /></a>}</span></td>
              <td className="px-3 py-3">
                <select aria-label={`Status for ${r.companyName}`} value={r.currentStatus} disabled={busyId === r.id} onChange={(e) => onStatus(r, e.target.value)} className="sr-only-select">
                  {STATUSES.map((s) => <option key={s}>{s}</option>)}
                </select>
                <StatusBadge status={r.currentStatus} />
              </td>
              <td className="whitespace-nowrap px-3 py-3 text-fg-muted">{formatDate(r.applicationDate)}</td>
              <td className={`whitespace-nowrap px-3 py-3 ${isOverdue(r.deadline) && !['Selected', 'Rejected', 'Withdrawn'].includes(r.currentStatus) ? 'text-danger' : 'text-fg-muted'}`}>{formatDate(r.deadline)}</td>
              <td className="px-3 py-3 text-fg-muted">{r.matchScore == null ? '—' : `${Math.round(r.matchScore)}%`}</td>
              <td className="px-3 py-3">{r.priority ? <PriorityBadge level={r.priority.level} /> : '—'}</td>
              <td className="max-w-[180px] truncate px-3 py-3 text-fg-muted">{r.nextAction || '—'}</td>
            </tr>
          ))}
        </tbody>
      </table>
    </div>
  );
}
