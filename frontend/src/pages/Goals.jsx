import { useEffect, useState } from 'react';
import { goalService } from '../services/workspace';
import { apiError } from '../services/api';
import { useAsync } from '../hooks/useAsync';
import { useToast } from '../context/ToastContext';
import { Button } from '../components/ui/Button';
import { Card, ErrorState, PageHeader, ProgressBar, Skeleton } from '../components/ui/Display';

export default function Goals() {
  const toast = useToast();
  const { data, loading, error, reload } = useAsync(() => goalService.current(), []);
  const [target, setTarget] = useState(''); const [busy, setBusy] = useState(false);
  useEffect(() => { if (data?.target) setTarget(String(data.target)); }, [data]);
  const save = async (e) => {
    e.preventDefault(); const n = Number(target);
    if (!Number.isInteger(n) || n < 1 || n > 200) return toast.error('Enter a whole number between 1 and 200.');
    setBusy(true);
    try { data?.id ? await goalService.update(data.id, { target: n }) : await goalService.create({ target: n }); toast.success('Weekly goal saved.'); reload(); }
    catch (er) { toast.error(apiError(er).message); } finally { setBusy(false); }
  };
  return (
    <>
      <PageHeader title="Weekly goal" description="An optional target. Only applications you actually submitted count, not saved jobs." />
      {loading && <Skeleton className="h-40" />}
      {error && <ErrorState error={error} onRetry={reload} />}
      {!loading && !error && (
        <div className="grid gap-4 md:grid-cols-2">
          <Card title="Your target"><form onSubmit={save} className="flex items-end gap-3"><div className="flex-1"><label className="label" htmlFor="tg">Applications per week</label><input id="tg" inputMode="numeric" className="field" value={target} onChange={(e) => setTarget(e.target.value)} /></div><Button type="submit" loading={busy}>Save goal</Button></form></Card>
          <Card title={data?.weekStart ? `This week (${data.weekStart} to ${data.weekEnd})` : "This week"}>{data?.target ? (<><p className="font-display text-3xl font-semibold">{data.submitted} <span className="text-lg text-fg-muted">of {data.target}</span></p><ProgressBar className="my-3" value={(data.submitted / data.target) * 100} label="Weekly goal progress" /><p className="text-sm text-fg-muted">{Math.max(0, data.target - data.submitted)} remaining to reach your goal.</p></>) : <p className="text-sm text-fg-muted">Set a target to track this week's progress.</p>}</Card>
        </div>)}
    </>
  );
}
