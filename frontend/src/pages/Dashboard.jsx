import { Link } from 'react-router-dom';
import { Briefcase, CalendarClock, Link2, PlusCircle, Trophy, Clock, CalendarCheck } from 'lucide-react';
import { motion } from 'motion/react';
import { dashboardService } from '../services/workspace';
import { useAsync } from '../hooks/useAsync';
import { useAuth } from '../context/AuthContext';
import { Button } from '../components/ui/Button';
import { Card, EmptyState, ErrorState, PageHeader, PriorityBadge, Skeleton } from '../components/ui/Display';
import { FunnelChart, SourcesChart } from '../components/Charts';
import { formatDateTime, pct, ratio } from '../utils/format';

function Stat({ icon: Icon, label, value, i }) {
  return (
    <motion.div
      initial={{ opacity: 0, y: 8 }}
      animate={{ opacity: 1, y: 0 }}
      transition={{ delay: i * 0.04, duration: 0.25 }}
      className="card relative overflow-hidden p-4 before:absolute before:inset-y-0 before:left-0 before:w-1 before:bg-primary/70"
    >
      <div className="flex items-center gap-2 text-sm text-fg-muted">
        <span className="flex h-7 w-7 items-center justify-center rounded-lg bg-primary-soft text-primary"><Icon size={15} /></span>
        {label}
      </div>
      <p className="mt-2 font-display text-3xl font-semibold tracking-tight text-fg">{value}</p>
    </motion.div>
  );
}

export default function Dashboard() {
  const { user } = useAuth();
  const { data, loading, error, reload } = useAsync(() => dashboardService.summary(), []);
  const first = user?.name?.split(' ')[0];

  if (loading) return (<><PageHeader title="Dashboard" /><div className="grid grid-cols-2 gap-3 lg:grid-cols-5">{Array.from({ length: 5 }, (_, i) => <Skeleton key={i} className="h-24" />)}</div><Skeleton className="mt-4 h-72" /></>);
  if (error) return (<><PageHeader title="Dashboard" /><ErrorState error={error} onRetry={reload} /></>);

  if (!data || data.total === 0) return (
    <>
      <PageHeader title={first ? `Welcome, ${first}` : 'Welcome'} description="Your docket is empty — as it should be." />
      <EmptyState icon={Briefcase} title="No applications yet"
        description="Add your first application and this page fills in with your funnel, deadlines, sources and the actions that need you. Everything here comes from records you add."
        actions={<><Link to="/app/applications/new"><Button icon={PlusCircle}>Add application</Button></Link><Link to="/app/applications/new?mode=link"><Button variant="secondary" icon={Link2}>Autofill from job link</Button></Link></>} />
    </>
  );

  const interviewRate = ratio(data.reachedInterview, data.total);
  const offerRate = ratio(data.offers, data.reachedInterview);
  return (
    <>
      <PageHeader title={first ? `Hello, ${first}` : 'Dashboard'} description="Everything below is calculated from your own records." />
      <div className="grid grid-cols-2 gap-3 lg:grid-cols-5">
        <Stat i={0} icon={Briefcase} label="Total applications" value={data.total} />
        <Stat i={1} icon={CalendarCheck} label="Submitted this week" value={data.thisWeek} />
        <Stat i={2} icon={CalendarClock} label="Interviews scheduled" value={data.interviewsScheduled} />
        <Stat i={3} icon={Trophy} label="Offers received" value={data.offers} />
        <Stat i={4} icon={Clock} label="Deadlines in 14 days" value={data.upcomingDeadlines} />
      </div>

      <div className="mt-4 grid gap-4 lg:grid-cols-2">
        <Card title="Needs action">
          {data.actions?.length ? (
            <ul className="divide-y divide-border">
              {data.actions.map((a, i) => (
                <li key={i} className="flex items-start justify-between gap-3 py-3 first:pt-0 last:pb-0">
                  <div className="min-w-0"><p className="truncate text-sm font-medium">{a.company} — {a.position}</p><p className="text-sm text-fg-muted">{a.message}</p><p className="text-xs text-fg-subtle">{formatDateTime(a.date)}</p></div>
                  <div className="flex shrink-0 flex-col items-end gap-2"><PriorityBadge level={a.urgency} /><Link className="text-sm font-medium underline-offset-2 hover:underline" to={`/app/applications/${a.applicationId}`}>{a.actionLabel || 'Open'}</Link></div>
                </li>))}
            </ul>) : <p className="text-sm text-fg-muted">Nothing time-sensitive right now.</p>}
        </Card>
        <Card title="Recent activity">
          {data.recent?.length ? (
            <ul className="space-y-3">{data.recent.map((r, i) => (<li key={i} className="text-sm"><Link to={`/app/applications/${r.applicationId}`} className="hover:underline">{r.message}</Link><p className="text-xs text-fg-subtle">{formatDateTime(r.at)}</p></li>))}</ul>
          ) : <p className="text-sm text-fg-muted">No activity yet.</p>}
        </Card>
        <Card title="Application funnel">
          <FunnelChart data={data.funnel || []} />
          <p className="mt-2 text-xs text-fg-subtle">Each bar counts applications that have reached that stage or beyond. Rejected and withdrawn are excluded from later stages.</p>
        </Card>
        <Card title="Sources">
          {data.sources?.length ? <SourcesChart data={data.sources} /> : <p className="text-sm text-fg-muted">Add a source to your applications to compare where they come from.</p>}
          <dl className="mt-3 grid grid-cols-2 gap-3 text-sm">
            <div><dt className="text-fg-muted">Interview rate</dt><dd className="font-medium">{interviewRate === null ? '—' : pct(interviewRate)}</dd><dd className="text-xs text-fg-subtle">reached interview ÷ all applications</dd></div>
            <div><dt className="text-fg-muted">Offer rate</dt><dd className="font-medium">{offerRate === null ? '—' : pct(offerRate)}</dd><dd className="text-xs text-fg-subtle">offers ÷ reached interview</dd></div>
          </dl>
        </Card>
      </div>
    </>
  );
}
