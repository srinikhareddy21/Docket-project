import { useState } from 'react';
import { Link } from 'react-router-dom';
import { CalendarClock } from 'lucide-react';
import { interviewService } from '../services/workspace';
import { useAsync } from '../hooks/useAsync';
import { Button } from '../components/ui/Button';
import { EmptyState, ErrorState, PageHeader, SkeletonRows } from '../components/ui/Display';
import { formatDate, formatDateTime } from '../utils/format';

const TABS = [
  ['upcoming', 'Upcoming', (i, now) => new Date(i.interviewDate).getTime() >= now.getTime()],
  ['completed', 'Completed', (i, now) => new Date(i.interviewDate).getTime() < now.getTime() && ['Passed', 'Failed'].includes(i.result)],
  ['awaiting', 'Awaiting outcome', (i, now) => new Date(i.interviewDate).getTime() < now.getTime() && !['Passed', 'Failed'].includes(i.result)],
  ['followup', 'Needs follow-up', (i) => {
    if (!i.followUpDate) return false;
    const end = new Date(i.followUpDate + 'T23:59:59');
    return end.getTime() >= Date.now() - 86400000;
  }],
];

export default function Interviews() {
  const { data, loading, error, reload } = useAsync(() => interviewService.list(), []);
  const [tab, setTab] = useState('upcoming');
  const now = new Date();
  const fn = TABS.find((t) => t[0] === tab)[2];
  const rows = (data || []).filter((i) => fn(i, now)).sort((a, b) => new Date(a.interviewDate) - new Date(b.interviewDate));
  return (
    <>
      <PageHeader title="Interviews" description="Every interview and assessment round across your applications. Add or edit rounds from an application page." />
      <div role="tablist" className="mb-5 inline-flex flex-wrap rounded-control border border-border bg-surface-muted p-1">
        {TABS.map(([k, l, f]) => (
          <button key={k} role="tab" aria-selected={tab === k} onClick={() => setTab(k)}
            className={`rounded-[7px] px-3.5 py-1.5 text-sm ${tab === k ? 'bg-surface font-medium shadow-card' : 'text-fg-muted hover:text-fg'}`}>
            {l}{data ? <span className="ml-1.5 text-xs text-fg-subtle">{data.filter((i) => f(i, now)).length}</span> : null}
          </button>
        ))}
      </div>
      {loading && <SkeletonRows rows={3} />}
      {error && <ErrorState error={error} onRetry={reload} />}
      {!loading && !error && rows.length === 0 && (
        <EmptyState
          icon={CalendarClock}
          title={data?.length ? 'Nothing in this view' : 'No interviews yet'}
          description={data?.length
            ? 'Interviews matching this view will appear here.'
            : 'Open an application and choose Add round to record an interview or assessment with a date and time. Setting status alone does not create a round.'}
          actions={<Link to="/app/applications"><Button variant="secondary">Go to applications</Button></Link>}
        />
      )}
      {rows.length > 0 && (
        <ul className="space-y-2">
          {rows.map((i) => (
            <li key={i.id} className="card flex flex-wrap items-center justify-between gap-3 p-4">
              <div>
                <Link to={`/app/applications/${i.applicationId}`} className="font-medium hover:underline">
                  {i.companyName} — {i.position}
                </Link>
                <p className="text-sm text-fg-muted">
                  {i.round === 'Assessment' ? 'Assessment' : `${i.round} interview`}
                  {i.interviewMode ? ` · ${i.interviewMode}` : ''} · {formatDateTime(i.interviewDate)}
                </p>
              </div>
              <div className="text-right text-sm">
                <p>{i.result}</p>
                {i.followUpDate && <p className="text-xs text-fg-subtle">Follow up {formatDate(i.followUpDate)}</p>}
              </div>
            </li>
          ))}
        </ul>
      )}
    </>
  );
}
