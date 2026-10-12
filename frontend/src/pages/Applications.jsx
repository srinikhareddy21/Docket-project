import { useEffect, useState } from 'react';
import { Link, useSearchParams } from 'react-router-dom';
import { Briefcase, Download, Filter, PlusCircle, SearchX } from 'lucide-react';
import { applicationService } from '../services/applications';
import { apiError } from '../services/api';
import { useAsync } from '../hooks/useAsync';
import { useDebounce } from '../hooks/useDebounce';
import { useToast } from '../context/ToastContext';
import { Button } from '../components/ui/Button';
import { Select } from '../components/ui/Field';
import { EmptyState, ErrorState, PageHeader, SkeletonRows } from '../components/ui/Display';
import AppTable from '../components/AppTable';
import { JOB_TYPES, SOURCES, STATUSES, WORK_MODES } from '../config/constants';

const SORTS = [
  { value: 'applicationDate,desc', label: 'Newest applied' }, { value: 'applicationDate,asc', label: 'Oldest applied' },
  { value: 'deadline,asc', label: 'Deadline (soonest)' }, { value: 'companyName,asc', label: 'Company A–Z' }, { value: 'priority,desc', label: 'Priority (highest)' },
];

export default function Applications() {
  const toast = useToast();
  const [sp, setSp] = useSearchParams();
  const [q, setQ] = useState(sp.get('q') || '');
  const dq = useDebounce(q, 350);
  const [filters, setFilters] = useState({ status: '', jobType: '', source: '', workMode: '', location: '' });
  const [sort, setSort] = useState(SORTS[0].value);
  const [page, setPage] = useState(0);
  const [selected, setSelected] = useState(new Set());
  const [bulkStatus, setBulkStatus] = useState('');
  const [busy, setBusy] = useState(null);
  const [showFilters, setShowFilters] = useState(false);

  useEffect(() => { setQ(sp.get('q') || ''); }, [sp]);
  useEffect(() => { if (sp.get('focus') === 'search') document.querySelector('[data-search-input]')?.focus(); }, [sp]);
  useEffect(() => { setPage(0); setSelected(new Set()); }, [dq, filters, sort]);

  const params = { q: dq || undefined, ...Object.fromEntries(Object.entries(filters).filter(([, v]) => v)), sort, page, size: 20 };
  const { data, loading, error, reload } = useAsync(() => applicationService.list(params), [JSON.stringify(params)]);
  const rows = data?.content || [];
  const hasFilters = !!dq || Object.values(filters).some(Boolean);

  const changeStatus = async (r, status) => {
    if (status === r.currentStatus) return;
    setBusy(r.id);
    try { await applicationService.setStatus(r.id, status); toast.success(`${r.companyName} moved to ${status}.`); reload(); }
    catch (e) { toast.error(apiError(e).message); } finally { setBusy(null); }
  };
  const applyBulk = async () => {
    if (!bulkStatus || !selected.size) return;
    try { await applicationService.bulkStatus([...selected], bulkStatus); toast.success(`${selected.size} application(s) updated to ${bulkStatus}.`); setSelected(new Set()); setBulkStatus(''); reload(); }
    catch (e) { toast.error(apiError(e).message); }
  };
  const exportCsv = async () => {
    try {
      const res = await applicationService.exportCsv();
      const url = URL.createObjectURL(res.data); const a = document.createElement('a');
      a.href = url; a.download = 'docket-applications.csv'; a.click(); URL.revokeObjectURL(url);
      toast.success('Export downloaded.');
    } catch (e) { toast.error(apiError(e).message); }
  };
  const toggle = (id) => setSelected((s) => { const n = new Set(s); n.has(id) ? n.delete(id) : n.add(id); return n; });
  const toggleAll = () => setSelected((s) => (rows.every((r) => s.has(r.id)) ? new Set() : new Set(rows.map((r) => r.id))));
  const setF = (k) => (e) => setFilters((f) => ({ ...f, [k]: e.target.value }));

  return (
    <>
      <PageHeader title="Applications" description="Search, filter and update every application in one list."
        actions={<><Button variant="secondary" icon={Download} onClick={exportCsv}>Export CSV</Button><Link to="/app/applications/new"><Button icon={PlusCircle}>Add application</Button></Link></>} />

      <div className="mb-4 flex flex-wrap items-end gap-3">
        <div className="min-w-[220px] flex-1">
          <label htmlFor="list-search" className="label">Search</label>
          <input id="list-search" data-search-input className="field" value={q} onChange={(e) => { setQ(e.target.value); if (sp.get('q')) setSp({}); }} placeholder="Company, title, description, notes" />
        </div>
        <Select label="Sort by" value={sort} onChange={(e) => setSort(e.target.value)} options={SORTS} />
        <Button variant="secondary" icon={Filter} onClick={() => setShowFilters((s) => !s)} aria-expanded={showFilters}>Filters</Button>
      </div>
      {showFilters && (
        <div className="card mb-4 grid gap-3 p-4 sm:grid-cols-2 lg:grid-cols-5">
          <Select label="Status" placeholder="Any" value={filters.status} onChange={setF('status')} options={STATUSES} />
          <Select label="Job type" placeholder="Any" value={filters.jobType} onChange={setF('jobType')} options={JOB_TYPES} />
          <Select label="Source" placeholder="Any" value={filters.source} onChange={setF('source')} options={SOURCES} />
          <Select label="Work mode" placeholder="Any" value={filters.workMode} onChange={setF('workMode')} options={WORK_MODES} />
          <div><label className="label" htmlFor="f-loc">Location</label><input id="f-loc" className="field" value={filters.location} onChange={setF('location')} /></div>
        </div>
      )}

      {selected.size > 0 && (
        <div className="card mb-3 flex flex-wrap items-center gap-3 bg-primary-soft/60 p-3" role="region" aria-label="Bulk actions">
          <span className="text-sm font-medium">{selected.size} selected</span>
          <select aria-label="New status" className="field w-auto" value={bulkStatus} onChange={(e) => setBulkStatus(e.target.value)}><option value="">Change status to…</option>{STATUSES.map((s) => <option key={s}>{s}</option>)}</select>
          <Button size="sm" disabled={!bulkStatus} onClick={applyBulk}>Apply</Button>
          <Button size="sm" variant="ghost" onClick={() => setSelected(new Set())}>Clear</Button>
        </div>
      )}

      {loading && <SkeletonRows />}
      {error && <ErrorState error={error} onRetry={reload} />}
      {!loading && !error && rows.length === 0 && (hasFilters
        ? <EmptyState icon={SearchX} title="No applications match" description="Try a different search or clear the filters." actions={<Button variant="secondary" onClick={() => { setQ(''); setFilters({ status: '', jobType: '', source: '', workMode: '', location: '' }); }}>Clear search and filters</Button>} />
        : <EmptyState icon={Briefcase} title="No applications yet" description="Applications you add will appear here." actions={<Link to="/app/applications/new"><Button icon={PlusCircle}>Add application</Button></Link>} />)}
      {!loading && !error && rows.length > 0 && (
        <>
          <AppTable rows={rows} selected={selected} onToggle={toggle} onToggleAll={toggleAll} onStatus={changeStatus} busyId={busy} />
          <div className="mt-4 flex items-center justify-between text-sm text-fg-muted">
            <span>{data.totalElements} application{data.totalElements === 1 ? '' : 's'}</span>
            <div className="flex items-center gap-2">
              <Button size="sm" variant="secondary" disabled={page === 0} onClick={() => setPage((p) => p - 1)}>Previous</Button>
              <span>Page {page + 1} of {Math.max(1, data.totalPages)}</span>
              <Button size="sm" variant="secondary" disabled={page + 1 >= data.totalPages} onClick={() => setPage((p) => p + 1)}>Next</Button>
            </div>
          </div>
        </>
      )}
    </>
  );
}
