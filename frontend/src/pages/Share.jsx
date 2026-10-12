import { useState } from 'react';
import { Copy, Share2 } from 'lucide-react';
import { shareService } from '../services/workspace';
import { apiError } from '../services/api';
import { useAsync } from '../hooks/useAsync';
import { useToast } from '../context/ToastContext';
import { Button } from '../components/ui/Button';
import { Card, EmptyState, ErrorState, PageHeader, SkeletonRows } from '../components/ui/Display';
import { ConfirmDialog } from '../components/ui/Modal';
import { formatDate } from '../utils/format';

const EXPIRY = [['7', '7 days'], ['30', '30 days'], ['90', '90 days']];

export default function Share() {
  const toast = useToast();
  const { data, loading, error, reload } = useAsync(() => shareService.list(), []);
  const [days, setDays] = useState('30'); const [busy, setBusy] = useState(false); const [revoke, setRevoke] = useState(null);
  const [fresh, setFresh] = useState(null);   // the full link, held in memory only; the server cannot show it again
  const copy = async (url) => { try { await navigator.clipboard.writeText(url); toast.success('Link copied.'); } catch { toast.error('Could not copy. Select the link and copy it manually.'); } };
  const create = async () => {
    setBusy(true);
    try { const l = await shareService.create({ expiresInDays: Number(days) }); setFresh(l.url); toast.success('Share link created.'); reload(); }
    catch (e) { toast.error(apiError(e).message); } finally { setBusy(false); }
  };
  const doRevoke = async () => { try { await shareService.revoke(revoke.id); toast.success('Link revoked. It no longer works.'); setRevoke(null); setFresh(null); reload(); } catch (e) { toast.error(apiError(e).message); } };
  return (
    <>
      <PageHeader title="Share progress" description="Give a mentor or placement officer a read-only summary. It shows counts only — never notes, documents, company names or contact details." />
      <Card title="Create a link">
        <div className="flex flex-wrap items-end gap-3"><div><label className="label" htmlFor="exp">Expires after</label><select id="exp" className="field" value={days} onChange={(e) => setDays(e.target.value)}>{EXPIRY.map(([v, l]) => <option key={v} value={v}>{l}</option>)}</select></div><Button icon={Share2} loading={busy} onClick={create}>Create link</Button></div>
        {fresh && (
          <div className="mt-4 rounded-card border border-border-strong bg-surface-muted/60 p-3" role="status">
            <p className="text-sm font-medium">Copy your link now</p>
            <p className="text-xs text-fg-muted">For your security it is shown only once. If you lose it, revoke it and create a new one.</p>
            <div className="mt-2 flex gap-2"><input readOnly aria-label="Share link" className="field flex-1 text-xs" value={fresh} onFocus={(e) => e.target.select()} /><Button size="sm" variant="secondary" icon={Copy} onClick={() => copy(fresh)}>Copy</Button></div>
          </div>)}
      </Card>
      <h2 className="mb-3 mt-6 text-base font-semibold">Your links</h2>
      {loading && <SkeletonRows rows={2} />}
      {error && <ErrorState error={error} onRetry={reload} />}
      {data?.length === 0 && <EmptyState icon={Share2} title="No share links" description="Links you create will be listed here with their status." />}
      {data?.length > 0 && <ul className="space-y-2">{data.map((l) => (
        <li key={l.id} className="card flex flex-wrap items-center justify-between gap-3 p-4"><div><p className="text-sm font-medium">{l.status}</p><p className="text-xs text-fg-muted">Created {formatDate(l.createdAt)} · {l.status === 'Active' ? 'expires' : 'expired or ended'} {formatDate(l.expiresAt)}</p></div>
          {l.status === 'Active' && <Button size="sm" variant="ghost" onClick={() => setRevoke(l)}>Revoke</Button>}</li>))}</ul>}
      <ConfirmDialog open={!!revoke} onClose={() => setRevoke(null)} onConfirm={doRevoke} danger title="Revoke this link?" message="Anyone using this link will immediately lose access." confirmLabel="Revoke link" />
    </>
  );
}
