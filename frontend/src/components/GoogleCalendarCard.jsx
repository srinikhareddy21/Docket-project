import { useEffect, useState } from 'react';
import { useSearchParams } from 'react-router-dom';
import { CalendarCheck, RefreshCw } from 'lucide-react';
import { calendarService } from '../services/workspace';
import { apiError } from '../services/api';
import { useAsync } from '../hooks/useAsync';
import { useToast } from '../context/ToastContext';
import { Button } from './ui/Button';
import { ConfirmDialog } from './ui/Modal';
import { Card, ErrorState, Skeleton, UnavailableNotice } from './ui/Display';
import { formatDateTime } from '../utils/format';

const RESULT = {
  connected: ['success', 'Google Calendar connected. Use "Sync now", or wait for the next automatic sync.'],
  denied: ['error', 'Google Calendar was not connected: calendar access was not granted.'],
  expired: ['error', 'That connection attempt expired. Start again.'],
  failed: ['error', 'Google Calendar could not be connected. Try again.'],
};
const SYNC_LABEL = { OK: 'Synced', PARTIAL: 'Partly synced', FAILED: 'Sync failed' };

/** Real Google Calendar connection. Every state comes from the server; nothing here is simulated. */
export default function GoogleCalendarCard() {
  const toast = useToast();
  const [sp, setSp] = useSearchParams();
  const { data, loading, error, reload, setData } = useAsync(() => calendarService.status(), []);
  const [busy, setBusy] = useState('');
  const [confirm, setConfirm] = useState(false);
  const [removeEvents, setRemoveEvents] = useState(true);

  useEffect(() => {
    const r = sp.get('google');
    if (!r) return;
    const [kind, msg] = RESULT[r] || RESULT.failed;
    toast[kind](msg);
    const next = new URLSearchParams(sp); next.delete('google'); setSp(next, { replace: true });
    reload();
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, []);

  const run = async (key, fn, ok) => {
    setBusy(key);
    try { const r = await fn(); if (ok) toast.success(typeof ok === 'function' ? ok(r) : ok); return r; }
    catch (e) { toast.error(apiError(e).message); } finally { setBusy(''); }
  };
  const connect = async () => { const r = await run('connect', () => calendarService.connect()); if (r?.url) window.location.assign(r.url); };
  const sync = async () => { const r = await run('sync', () => calendarService.sync()); if (r) { setData(r); r.lastSyncStatus === 'OK' ? toast.success(`Sync finished. ${r.lastSyncNote}`) : toast.error(`${r.lastSyncNote || 'Sync did not complete.'}`); } };
  const setPref = async (k, v) => {
    const next = { syncInterviews: data.syncInterviews, syncFollowUps: data.syncFollowUps, syncDeadlines: data.syncDeadlines, [k]: v };
    const r = await run('prefs', () => calendarService.savePrefs(next), 'Preferences saved. They apply from the next sync.');
    if (r) setData(r);
  };
  const disconnect = async () => {
    const r = await run('disc', () => calendarService.disconnect(removeEvents));
    setConfirm(false);
    if (r) { toast.success(removeEvents ? `Disconnected. ${r.eventsRemoved} event(s) removed${r.eventsFailed ? `, ${r.eventsFailed} could not be removed` : ''}.` : 'Disconnected. Existing events stay in your calendar.'); reload(); }
  };

  return (
    <Card title="Google Calendar">
      {loading ? <Skeleton className="h-28" /> : error ? <ErrorState error={error} onRetry={reload} /> : !data.configured ? (
        <div className="space-y-3">
          <UnavailableNotice title="Not set up on this server">{data.message || 'Google Calendar credentials are missing on this server.'}</UnavailableNotice>
          <div className="rounded-card border border-border bg-surface-muted/50 p-3 text-xs text-fg-muted">
            <p className="font-medium text-fg">Required environment variables</p>
            <ul className="mt-1 list-disc space-y-0.5 pl-4 font-mono">
              <li>GOOGLE_CLIENT_ID</li>
              <li>GOOGLE_CLIENT_SECRET</li>
              <li>GOOGLE_REDIRECT_URI (…/api/integrations/google-calendar/callback)</li>
              <li>TOKEN_ENCRYPTION_KEY (openssl rand -base64 32)</li>
            </ul>
            <p className="mt-2">Create an OAuth client in Google Cloud Console with the Calendar API enabled, set these values, restart the backend, then connect here. DOCKET never invents a connected state.</p>
          </div>
        </div>
      ) : data.status === 'NOT_CONNECTED' ? (
        <div className="space-y-3">
          <p className="text-sm text-fg-muted">Add your interviews, follow-ups and deadlines to your primary Google Calendar. DOCKET asks only for permission to manage events it creates and sends just the company, role, round, mode and meeting link. Notes and feedback never leave DOCKET.</p>
          <Button icon={CalendarCheck} loading={busy === 'connect'} onClick={connect}>Connect Google Calendar</Button>
        </div>
      ) : (
        <div className="space-y-4">
          {!data.connected && <div role="alert" className="rounded-card border border-danger/40 bg-danger/10 p-3 text-sm"><p className="font-medium">Reconnect needed</p><p className="text-fg-muted">{data.message}</p><Button className="mt-2" size="sm" loading={busy === 'connect'} onClick={connect}>Reconnect</Button></div>}
          <dl className="grid grid-cols-2 gap-2 text-sm">
            <div><dt className="text-fg-muted">Connected</dt><dd>{formatDateTime(data.connectedAt)}</dd></div>
            <div><dt className="text-fg-muted">Events in your calendar</dt><dd>{data.syncedEvents}</dd></div>
            <div className="col-span-2"><dt className="text-fg-muted">Last sync</dt><dd>{data.lastSyncAt ? `${SYNC_LABEL[data.lastSyncStatus] || data.lastSyncStatus} · ${formatDateTime(data.lastSyncAt)}` : 'Not synced yet'}{data.lastSyncNote ? <span className="block text-xs text-fg-subtle">{data.lastSyncNote}</span> : null}</dd></div>
          </dl>
          <fieldset className="space-y-2" disabled={busy === 'prefs'}>
            <legend className="label">What to sync</legend>
            {[['syncInterviews', 'Interviews'], ['syncFollowUps', 'Follow-ups'], ['syncDeadlines', 'Application deadlines']].map(([k, label]) => (
              <label key={k} className="flex items-center gap-2 text-sm"><input type="checkbox" className="accent-[rgb(var(--color-primary))]" checked={data[k]} onChange={(e) => setPref(k, e.target.checked)} />{label}</label>))}
            <p className="text-xs text-fg-subtle">Turning one off removes its events from your calendar at the next sync. Only open applications are synced; past events stay.</p>
          </fieldset>
          <div className="flex flex-wrap gap-2">
            <Button icon={RefreshCw} loading={busy === 'sync'} disabled={!data.connected} onClick={sync}>Sync now</Button>
            <Button variant="secondary" onClick={() => setConfirm(true)}>Disconnect</Button>
          </div>
          <p className="text-xs text-fg-subtle">Syncs automatically about every 30 minutes.</p>
        </div>)}
      <ConfirmDialog open={confirm} onClose={() => setConfirm(false)} onConfirm={disconnect} loading={busy === 'disc'} danger title="Disconnect Google Calendar?" confirmLabel="Disconnect"
        message={<span className="block space-y-3"><span className="block">DOCKET will stop syncing and ask Google to revoke its access.</span>
          <label className="flex items-center gap-2 text-sm"><input type="checkbox" className="accent-[rgb(var(--color-primary))]" checked={removeEvents} onChange={(e) => setRemoveEvents(e.target.checked)} />Also remove the events DOCKET added to my calendar</label></span>} />
    </Card>
  );
}
