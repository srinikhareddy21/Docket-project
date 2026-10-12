import { useParams } from 'react-router-dom';
import { Logo } from '../components/ui/Logo';
import { useAsync } from '../hooks/useAsync';
import { shareService } from '../services/workspace';
import { Skeleton } from '../components/ui/Display';
import { formatDate } from '../utils/format';

export default function SharedProgress() {
  const { token } = useParams();
  const { data, loading, error } = useAsync(() => shareService.publicView(token), [token]);
  return (
    <div className="mx-auto max-w-3xl px-4 py-10">
      <Logo className="mb-8" />
      {loading && <Skeleton className="h-48 w-full" />}
      {error && <div className="card p-6"><h1 className="text-xl font-semibold">{error.status === 404 || error.status === 410 ? 'This link is not available' : 'Could not load progress'}</h1><p className="mt-1 text-sm text-fg-muted">{error.status === 404 || error.status === 410 ? 'It may have expired or been revoked by its owner.' : error.message}</p></div>}
      {data && (
        <div className="space-y-4">
          <h1 className="text-2xl font-semibold">Job search progress</h1>
          <p className="text-sm text-fg-muted">Read-only summary{data.expiresAt ? ` · link expires ${formatDate(data.expiresAt)}` : ''}</p>
          <div className="grid grid-cols-2 gap-3 sm:grid-cols-4">
            {[['Applications', data.total], ['Interviews', data.interviews], ['Offers', data.offers], ['Shortlisted', data.statusCounts?.Shortlisted ?? 0]].map(([l, v]) => (
              <div key={l} className="card p-4"><p className="text-xs text-fg-muted">{l}</p><p className="mt-1 font-display text-2xl font-semibold">{v}</p></div>))}
          </div>
          <div className="card p-5"><h2 className="mb-3 text-base font-semibold">By status</h2>
            <ul className="space-y-1.5 text-sm">{Object.entries(data.statusCounts || {}).map(([s, n]) => <li key={s} className="flex justify-between"><span className="text-fg-muted">{s}</span><span>{n}</span></li>)}</ul>
          </div>
        </div>
      )}
    </div>
  );
}
