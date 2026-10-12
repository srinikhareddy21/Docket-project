import { useEffect, useState } from 'react';
import { reminderService } from '../services/workspace';
import { apiError } from '../services/api';
import { useAsync } from '../hooks/useAsync';
import { useToast } from '../context/ToastContext';
import { Button } from '../components/ui/Button';
import { Card, ErrorState, PageHeader, Skeleton } from '../components/ui/Display';
import GoogleCalendarCard from '../components/GoogleCalendarCard';
import ExtensionCard from '../components/ExtensionCard';

const DEFAULTS = { emailEnabled: false, deadlineLeadHours: 48, interviewLeadHours: 24, followUpEnabled: true, timezone: Intl.DateTimeFormat().resolvedOptions().timeZone };

export default function Settings() {
  const toast = useToast();
  const { data, loading, error, reload } = useAsync(() => reminderService.getPrefs(), []);
  const [p, setP] = useState(DEFAULTS); const [busy, setBusy] = useState(false);
  useEffect(() => { if (data) setP({ ...DEFAULTS, ...data }); }, [data]);
  const save = async () => { setBusy(true); try { await reminderService.savePrefs(p); toast.success('Reminder settings saved.'); } catch (e) { toast.error(apiError(e).message); } finally { setBusy(false); } };
  const num = (k) => (e) => setP((s) => ({ ...s, [k]: Math.max(1, Math.min(336, Number(e.target.value) || 1)) }));
  return (
    <>
      <PageHeader title="Settings" />
      <div className="grid gap-4 lg:grid-cols-2">
        <Card title="Email reminders">
          {loading ? <Skeleton className="h-40" /> : error ? <ErrorState error={error} onRetry={reload} /> : (
            <div className="space-y-4">
              <label className="flex items-center gap-2 text-sm"><input type="checkbox" className="accent-[rgb(var(--color-primary))]" checked={p.emailEnabled} onChange={(e) => setP({ ...p, emailEnabled: e.target.checked })} />Send reminders by email</label>
              <div className="grid grid-cols-2 gap-3"><div><label className="label" htmlFor="dl">Deadline reminder (hours before)</label><input id="dl" type="number" className="field" value={p.deadlineLeadHours} onChange={num('deadlineLeadHours')} /></div><div><label className="label" htmlFor="il">Interview reminder (hours before)</label><input id="il" type="number" className="field" value={p.interviewLeadHours} onChange={num('interviewLeadHours')} /></div></div>
              <label className="flex items-center gap-2 text-sm"><input type="checkbox" className="accent-[rgb(var(--color-primary))]" checked={p.followUpEnabled} onChange={(e) => setP({ ...p, followUpEnabled: e.target.checked })} />Remind me about follow-ups</label>
              <div><label className="label" htmlFor="tz">Time zone</label><input id="tz" className="field" value={p.timezone} onChange={(e) => setP({ ...p, timezone: e.target.value })} /><p className="mt-1 text-xs text-fg-subtle">A name like Asia/Kolkata. Follow-up reminders go out at 9:00 in this zone. Emails are sent only while this is on, and only for open applications.</p></div>
              <Button loading={busy} onClick={save}>Save settings</Button>
            </div>)}
        </Card>
        <div className="space-y-4">
          <GoogleCalendarCard />
          <ExtensionCard />
          <Card title="Keyboard shortcuts"><dl className="space-y-2 text-sm">{[['N', 'Add application'], ['/', 'Search applications'], ['?', 'Show shortcuts']].map(([k, d]) => <div key={k} className="flex justify-between"><dt className="text-fg-muted">{d}</dt><dd><kbd className="rounded border border-border-strong bg-surface-muted px-2 py-0.5 text-xs">{k}</kbd></dd></div>)}</dl></Card>
        </div>
      </div>
    </>
  );
}
