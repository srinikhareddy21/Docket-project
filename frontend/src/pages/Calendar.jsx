import { useState } from 'react';
import { Link } from 'react-router-dom';
import { Bell, X } from 'lucide-react';
import { reminderService } from '../services/workspace';
import { apiError } from '../services/api';
import { useAsync } from '../hooks/useAsync';
import { useToast } from '../context/ToastContext';
import { Button } from '../components/ui/Button';
import { EmptyState, ErrorState, PageHeader, SkeletonRows, UnavailableNotice, Card } from '../components/ui/Display';
import { formatDateTime } from '../utils/format';

const TYPE = { DEADLINE: 'Deadline', INTERVIEW: 'Interview', FOLLOW_UP: 'Follow-up', CUSTOM: 'Your reminder' };
const STATUS = { PENDING: 'Scheduled', SENT: 'Email accepted by mail server', FAILED: 'Email failed', SKIPPED: 'Not emailed' };

export default function Calendar() {
  const toast = useToast();
  const { data, loading, error, reload } = useAsync(() => reminderService.list(), []);
  const prefs = useAsync(() => reminderService.getPrefs(), []);
  const [title, setTitle] = useState(''); const [when, setWhen] = useState(''); const [busy, setBusy] = useState(false);
  const add = async (e) => {
    e.preventDefault(); setBusy(true);
    try { await reminderService.create({ title, scheduledAt: new Date(when).toISOString() }); setTitle(''); setWhen(''); toast.success('Reminder added.'); reload(); }
    catch (er) { toast.error(apiError(er).message); } finally { setBusy(false); }
  };
  const dismiss = async (r) => { try { await reminderService.update(r.id, { dismissed: true }); reload(); } catch (e) { toast.error(apiError(e).message); } };
  const emailOff = prefs.data && !prefs.data.emailEnabled;
  return (
    <>
      <PageHeader title="Calendar and reminders" description="Reminders for deadlines, interviews and follow-ups, built from your applications." />
      {emailOff && <p className="mb-4 rounded-card border border-border bg-surface-muted/60 p-3 text-sm">Email reminders are off, so nothing is emailed. <Link className="underline" to="/app/settings">Turn them on in Settings</Link>.</p>}
      <div className="grid gap-4 lg:grid-cols-3">
        <div className="lg:col-span-2">
          {loading && <SkeletonRows rows={4} />}
          {error && <ErrorState error={error} onRetry={reload} />}
          {data && data.length === 0 && <EmptyState icon={Bell} title="No reminders yet" description="Reminders are created automatically from deadlines, interviews and follow-up dates on open applications. Add those details on an application, or create a custom reminder here." actions={<Link className="text-sm underline" to="/app/settings">Reminder settings</Link>} />}
          {data?.length > 0 && <ul className="space-y-2">{data.map((r) => (
            <li key={r.id} className="card flex items-center justify-between gap-3 p-4">
              <div className="min-w-0">
                {r.applicationId ? <Link to={`/app/applications/${r.applicationId}`} className="font-medium hover:underline">{r.title}</Link> : <p className="font-medium">{r.title}</p>}
                <p className="text-sm text-fg-muted">{TYPE[r.type] || r.type} · {formatDateTime(r.type === 'FOLLOW_UP' ? r.scheduledAt : r.eventAt)}</p>
                <p className="text-xs text-fg-subtle">Reminder at {formatDateTime(r.scheduledAt)}{r.note ? ` · ${r.note}` : ''}</p>
              </div>
              <div className="flex shrink-0 items-center gap-2"><span className="text-xs text-fg-muted">{STATUS[r.deliveryStatus] || r.deliveryStatus}</span>
                <button type="button" className="rounded p-1 text-fg-muted hover:bg-surface-muted" aria-label={`Dismiss reminder: ${r.title}`} onClick={() => dismiss(r)}><X size={16} /></button></div>
            </li>))}</ul>}
        </div>
        <div className="space-y-4">
          <Card title="Add a reminder">
            <form onSubmit={add} className="space-y-3">
              <div><label className="label" htmlFor="rt">What</label><input id="rt" className="field" required maxLength={200} value={title} onChange={(e) => setTitle(e.target.value)} placeholder="Update resume before applying" /></div>
              <div><label className="label" htmlFor="rw">When</label><input id="rw" type="datetime-local" className="field" required value={when} onChange={(e) => setWhen(e.target.value)} /></div>
              <Button type="submit" loading={busy}>Add reminder</Button>
            </form>
          </Card>
          <Card title="Google Calendar"><p className="text-sm text-fg-muted">Connect, sync and disconnect in <Link className="underline" to="/app/settings">Settings</Link>. The reminders on this page are DOCKET records, not Google Calendar events.</p></Card>
        </div>
      </div>
    </>
  );
}
