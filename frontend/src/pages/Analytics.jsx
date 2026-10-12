import { Link } from 'react-router-dom';
import { BarChart3, Download, PlusCircle } from 'lucide-react';
import { dashboardService } from '../services/workspace';
import { applicationService } from '../services/applications';
import { apiError } from '../services/api';
import { useToast } from '../context/ToastContext';
import { useAsync } from '../hooks/useAsync';
import { Button } from '../components/ui/Button';
import { Card, EmptyState, ErrorState, PageHeader, SkeletonRows } from '../components/ui/Display';
import { FunnelChart, MatchChart, SourcesChart, StatusChart, TrendChart } from '../components/Charts';
import { pct, ratio } from '../utils/format';

export default function Analytics() {
  const toast = useToast();
  const { data, loading, error, reload } = useAsync(async () => {
    const [summary, funnel, sources, trends] = await Promise.all([dashboardService.summary(), dashboardService.funnel(), dashboardService.sources(), dashboardService.trends()]);
    return { summary, funnel, sources, trends };
  }, []);
  const exportCsv = async () => {
    try {
      const res = await dashboardService.exportAnalysisCsv();
      const url = URL.createObjectURL(res.data); const a = document.createElement('a');
      a.href = url; a.download = 'docket-analysis.csv'; a.click(); URL.revokeObjectURL(url);
      toast.success('Analysis export downloaded.');
    } catch (e) { toast.error(apiError(e).message); }
  };
  const exportBtn = <Button variant="secondary" icon={Download} onClick={exportCsv}>Export CSV</Button>;
  if (loading) return (<><PageHeader title="Analytics" /><SkeletonRows rows={4} /></>);
  if (error) return (<><PageHeader title="Analytics" /><ErrorState error={error} onRetry={reload} /></>);
  const s = data.summary;
  if (!s || s.total === 0) return (<><PageHeader title="Analytics" /><EmptyState icon={BarChart3} title="Not enough data yet" description="Charts appear once you have added applications. Nothing here is estimated or filled in for you." actions={<Link to="/app/applications/new"><Button icon={PlusCircle}>Add application</Button></Link>} /></>);
  const ir = ratio(s.reachedInterview, s.total); const or = ratio(s.offers, s.reachedInterview);
  const metric = (label, value, basis) => (<div className="card p-4"><p className="text-sm text-fg-muted">{label}</p><p className="mt-1 font-display text-2xl font-semibold">{value}</p><p className="mt-1 text-xs text-fg-subtle">{basis}</p></div>);
  const insight = (label, value, basis) => (<div className="card p-4"><p className="text-sm text-fg-muted">{label}</p><p className="mt-1 font-display text-2xl font-semibold">{value ?? 0}</p><p className="mt-1 text-xs text-fg-subtle">{basis}</p></div>);
  const scored = (s.skillMatch || []).slice(0, 4).reduce((n, b) => n + b.count, 0);
  return (
    <>
      <PageHeader title="Analytics" description="Calculated from your own applications. Each rate shows its denominator." actions={exportBtn} />
      <div className="grid gap-3 sm:grid-cols-2 lg:grid-cols-4">
        {metric('Interview rate', ir === null ? '—' : pct(ir), `${s.reachedInterview} reached interview ÷ ${s.total} applications`)}
        {metric('Offer rate', or === null ? '—' : pct(or), s.reachedInterview ? `${s.offers} offers ÷ ${s.reachedInterview} reached interview` : 'No interviews yet')}
        {metric('Offers', s.offers, `${s.total} applications in total`)}
        {metric('Skills scored', scored > 0 ? scored : '—', scored > 0 ? 'Applications with required skills listed (matched against your profile skills)' : 'Add required skills on applications and skills on your profile to score matches')}
      </div>
      <div className="mt-3 grid grid-cols-2 gap-3 lg:grid-cols-4">
        {insight('Deadlines passed', s.insights?.deadlinesOverdue, 'open applications')}
        {insight('Deadlines in 7 days', s.insights?.deadlinesNext7Days, 'open applications')}
        {insight('Follow-ups due', s.insights?.followUpsDue, 'due today or overdue')}
        {insight('Interviews awaiting outcome', s.insights?.interviewsAwaitingOutcome, 'past, no result recorded')}
      </div>
      <div className="mt-4 grid gap-4 lg:grid-cols-2">
        <Card title="Funnel"><FunnelChart data={data.funnel} /></Card>
        <Card title="Applications over time">{data.trends?.length > 1 ? <TrendChart data={data.trends} /> : <p className="text-sm text-fg-muted">A trend needs applications in at least two periods.</p>}</Card>
        <Card title="Current status">{s.statuses?.length ? <StatusChart data={s.statuses} /> : <p className="text-sm text-fg-muted">No statuses to show yet.</p>}</Card>
        <Card title="Skill match">
          {scored > 0 ? <MatchChart data={s.skillMatch} /> : <p className="text-sm text-fg-muted">Add your skills and list required skills on applications to see how well you match. Applications without required skills are not scored.</p>}
          {scored > 0 && <p className="mt-2 text-xs text-fg-subtle">Matched required skills ÷ total required skills. The last bar counts applications with no required skills listed.</p>}
        </Card>
        <Card title="Sources" className="lg:col-span-2">{data.sources?.length ? <SourcesChart data={data.sources} /> : <p className="text-sm text-fg-muted">Set a source on your applications to compare them.</p>}</Card>
      </div>
    </>
  );
}
