import { useEffect, useMemo, useState } from 'react';
import { Link, useNavigate, useParams, useSearchParams } from 'react-router-dom';
import { useForm } from 'react-hook-form';
import { zodResolver } from '@hookform/resolvers/zod';
import { AlertTriangle, Link2, Save } from 'lucide-react';
import { applicationService } from '../services/applications';
import { apiError } from '../services/api';
import { useAsync } from '../hooks/useAsync';
import { useToast } from '../context/ToastContext';
import { Button } from '../components/ui/Button';
import { Input, Select, Textarea } from '../components/ui/Field';
import { Card, ErrorState, PageHeader, SkeletonRows, UnavailableNotice } from '../components/ui/Display';
import { applicationSchema, quickAddSchema } from '../schemas/application';
import { JOB_TYPES, SOURCES, STATUSES, WORK_MODES } from '../config/constants';
import { clearDraft, loadDraft, saveDraft } from '../utils/drafts';
import { useDebounce } from '../hooks/useDebounce';

const today = () => new Date().toISOString().slice(0, 10);
const EMPTY_FULL = { companyName: '', position: '', jobType: 'Internship', workMode: 'Onsite', location: '', jobDescription: '', requiredSkills: '', applicationSource: '', applicationDate: today(), deadline: '', applicationLink: '', salaryOrStipend: '', recruiterDetails: '', notes: '', currentStatus: 'Applied', resumeVersion: '', followUpDate: '' };
const EMPTY_QUICK = { companyName: '', position: '', applicationLink: '', currentStatus: 'Applied', applicationDate: today() };

// The API wants null (not "") for empty optional fields, a UTC instant for the deadline, and a skills array.
const clean = (o) => Object.fromEntries(Object.entries(o).map(([k, v]) => [k, typeof v === 'string' && v.trim() === '' ? null : v]));
const localToIso = (v) => (v ? new Date(v).toISOString() : null);
const isoToLocal = (iso) => { if (!iso) return ''; const d = new Date(iso); const p = (n) => String(n).padStart(2, '0'); return `${d.getFullYear()}-${p(d.getMonth() + 1)}-${p(d.getDate())}T${p(d.getHours())}:${p(d.getMinutes())}`; };
const toPayload = (v) => ({ ...clean(v), deadline: localToIso(v.deadline), requiredSkills: (v.requiredSkills || '').split(/[,\n;]/).map((s) => s.trim()).filter(Boolean) });
const fromRecord = (r) => ({ ...EMPTY_FULL, ...Object.fromEntries(Object.entries(r).filter(([k]) => k in EMPTY_FULL).map(([k, v]) => [k, v ?? ''])), requiredSkills: (r.requiredSkills || []).join(', '), applicationDate: r.applicationDate?.slice(0, 10) || '', followUpDate: r.followUpDate?.slice(0, 10) || '', deadline: isoToLocal(r.deadline) });

function DuplicateWarning({ matches }) {
  if (!matches?.length) return null;
  return (
    <div role="alert" className="flex gap-3 rounded-card border border-warning/40 bg-warning/10 p-3 text-sm">
      <AlertTriangle size={18} className="mt-0.5 shrink-0 text-warning" />
      <div><p className="font-medium">This may already be in your docket</p>
        <ul className="mt-1 space-y-0.5">{matches.map((m) => <li key={m.id}><Link to={`/app/applications/${m.id}`} target="_blank" className="underline">{m.companyName} — {m.position}</Link> <span className="text-fg-muted">({m.currentStatus})</span></li>)}</ul>
        <p className="mt-1 text-fg-muted">You can still save if this is a separate application.</p></div>
    </div>
  );
}

function useDuplicates(values, excludeId) {
  const dbl = useDebounce({ c: values.companyName, p: values.position, u: values.applicationLink }, 600);
  const { data } = useAsync(async () => {
    if (!(dbl.u || (dbl.c && dbl.p))) return [];
    try { return await applicationService.checkDuplicates({ company: dbl.c, position: dbl.p, url: dbl.u, excludeId }); } catch { return []; }
  }, [dbl.c, dbl.p, dbl.u, excludeId]);
  return data || [];
}

function QuickAdd({ onSaved }) {
  const toast = useToast();
  const draft = useMemo(() => loadDraft('quick'), []);
  const { register, handleSubmit, watch, reset, formState: { errors, isSubmitting } } = useForm({ resolver: zodResolver(quickAddSchema), defaultValues: draft || EMPTY_QUICK });
  const values = watch(); const dups = useDuplicates(values);
  useEffect(() => { const t = setTimeout(() => saveDraft('quick', values), 500); return () => clearTimeout(t); }, [JSON.stringify(values)]); // eslint-disable-line
  const onSubmit = async (v) => {
    try { const saved = await applicationService.create(clean(v)); clearDraft('quick'); reset(EMPTY_QUICK); toast.success('Application saved.'); onSaved(saved); }
    catch (e) { toast.error(apiError(e).message); }
  };
  return (
    <form onSubmit={handleSubmit(onSubmit)} className="space-y-4" noValidate>
      {draft && <p className="text-xs text-fg-subtle">Unsaved draft restored. It is stored only in this browser until you save.</p>}
      <div className="grid gap-4 sm:grid-cols-2">
        <Input label="Company" required placeholder="Company name" error={errors.companyName?.message} {...register('companyName')} />
        <Input label="Role" required placeholder="Job title" error={errors.position?.message} {...register('position')} />
        <Input label="Job link" placeholder="https://" error={errors.applicationLink?.message} {...register('applicationLink')} />
        <Select label="Status" options={STATUSES} {...register('currentStatus')} />
        <Input label="Application date" type="date" {...register('applicationDate')} />
      </div>
      <DuplicateWarning matches={dups} />
      <Button type="submit" icon={Save} loading={isSubmitting}>Save application</Button>
    </form>
  );
}

function FullForm({ initial, id, onSaved, draftKey = 'full' }) {
  const toast = useToast();
  const draft = useMemo(() => (id ? null : loadDraft(draftKey)), [id, draftKey]);
  const { register, handleSubmit, watch, reset, formState: { errors, isSubmitting } } = useForm({ resolver: zodResolver(applicationSchema), defaultValues: initial || draft || EMPTY_FULL });
  const { data: sugg } = useAsync(async () => { try { return await applicationService.suggestions(); } catch { return { companies: [], roles: [] }; } }, []);
  const values = watch(); const dups = useDuplicates(values, id);
  useEffect(() => { if (id) return undefined; const t = setTimeout(() => saveDraft(draftKey, values), 500); return () => clearTimeout(t); }, [JSON.stringify(values)]); // eslint-disable-line

  const onSubmit = async (v) => {
    try {
      const body = toPayload(v);
      const saved = id ? await applicationService.update(id, body) : await applicationService.create(body);
      if (!id) { clearDraft(draftKey); reset(EMPTY_FULL); }
      toast.success(id ? 'Changes saved.' : 'Application saved.'); onSaved(saved);
    } catch (e) { toast.error(apiError(e).message); }
  };
  const err = (k) => errors[k]?.message;
  return (
    <form onSubmit={handleSubmit(onSubmit)} className="space-y-6" noValidate>
      {draft && <p className="text-xs text-fg-subtle">Unsaved draft restored. It is stored only in this browser until you save.</p>}
      <Card title="Role">
        <div className="grid gap-4 sm:grid-cols-2">
          <div><Input label="Company" required list="company-list" error={err('companyName')} {...register('companyName')} /><datalist id="company-list">{(sugg?.companies || []).map((c) => <option key={c} value={c} />)}</datalist></div>
          <div><Input label="Job title" required list="role-list" error={err('position')} {...register('position')} /><datalist id="role-list">{(sugg?.roles || []).map((c) => <option key={c} value={c} />)}</datalist></div>
          <Select label="Job type" placeholder="Not specified" options={JOB_TYPES} {...register('jobType')} />
          <Select label="Work mode" placeholder="Not specified" options={WORK_MODES} {...register('workMode')} />
          <Input label="Location" placeholder="City, country" {...register('location')} />
          <Input label="Salary or stipend" placeholder="Optional" {...register('salaryOrStipend')} />
          <div className="sm:col-span-2"><Textarea label="Job description" rows={6} {...register('jobDescription')} /></div>
          <div className="sm:col-span-2"><Textarea label="Required skills" rows={2} hint="Separate with commas. Used to calculate your skill match." placeholder="Java, SQL, Spring Boot" {...register('requiredSkills')} /></div>
        </div>
      </Card>
      <Card title="Application">
        <div className="grid gap-4 sm:grid-cols-2">
          <Select label="Status" options={STATUSES} {...register('currentStatus')} />
          <Select label="Source" placeholder="Select a source" options={SOURCES} {...register('applicationSource')} />
          <Input label="Application date" type="date" {...register('applicationDate')} />
          <Input label="Deadline" type="datetime-local" {...register('deadline')} />
          <div className="sm:col-span-2"><Input label="Original job link" placeholder="https://" error={err('applicationLink')} {...register('applicationLink')} /></div>
          <Input label="Follow-up date" type="date" {...register('followUpDate')} />
          <Input label="Resume version used" placeholder="e.g. Resume v3 – backend" {...register('resumeVersion')} />
          <Input label="Recruiter or referral" placeholder="Optional" {...register('recruiterDetails')} />
          <div className="sm:col-span-2"><Textarea label="Notes" rows={4} {...register('notes')} /></div>
        </div>
        <p className="mt-3 text-xs text-fg-subtle">You can attach documents from the application page once it is saved.</p>
      </Card>
      <DuplicateWarning matches={dups} />
      <div className="flex gap-2"><Button type="submit" icon={Save} loading={isSubmitting}>{id ? 'Save changes' : 'Save application'}</Button></div>
    </form>
  );
}

const FIELD_LABELS = { companyName: 'Company', position: 'Job title', jobType: 'Job type', workMode: 'Work mode', location: 'Location', jobDescription: 'Description', requiredSkills: 'Required skills', deadline: 'Deadline', salaryOrStipend: 'Salary or stipend' };

function LinkAutofill({ onPrefill }) {
  const toast = useToast(); const [url, setUrl] = useState(''); const [loading, setLoading] = useState(false);
  const [msg, setMsg] = useState(null); const [result, setResult] = useState(null);
  const fetchDetails = async () => {
    setMsg(null); setResult(null); setLoading(true);
    try {
      const d = await applicationService.autofill(url.trim());
      if (!d.found) { setMsg({ tone: 'warning', text: d.message }); return; }
      const f = d.fields || {};
      setResult({ ...d, prefill: { ...EMPTY_FULL, ...f, requiredSkills: (f.requiredSkills || []).join(', '), deadline: isoToLocal(f.deadline), applicationLink: url.trim() } });
      toast.info('Details found. Review and correct them before saving.');
    } catch (e) {
      const err = apiError(e);
      setMsg({ tone: 'warning', text: err.fieldErrors?.url || err.message });
    } finally { setLoading(false); }
  };
  return (
    <div className="space-y-3">
      <div className="flex flex-wrap items-end gap-2">
        <div className="min-w-[240px] flex-1"><Input label="Job posting link" placeholder="https://company.com/careers/role" value={url} onChange={(e) => { setUrl(e.target.value); setResult(null); }} /></div>
        <Button icon={Link2} loading={loading} disabled={!/^https?:\/\/\S+/.test(url.trim())} onClick={fetchDetails}>Fetch details</Button>
      </div>
      {msg && <p role="alert" className="rounded-control bg-warning/10 px-3 py-2 text-sm text-warning">{msg.text} <button type="button" className="underline" onClick={() => onPrefill({ ...EMPTY_FULL, applicationLink: url.trim() })}>Continue with the link only</button></p>}
      {result && (
        <div className="rounded-card border border-border bg-surface-muted p-4 text-sm" role="status">
          <p className="font-medium">{result.message}</p>
          <p className="mt-1 text-fg-muted">Found on the page: {result.extractedFields.map((k) => FIELD_LABELS[k] || k).join(', ')}. Everything else stays at the form default or blank, including any field not listed here, so check job type, work mode and source.</p>
          {result.warnings?.length > 0 && <ul className="mt-2 list-disc space-y-0.5 pl-5 text-warning">{result.warnings.map((w) => <li key={w}>{w}</li>)}</ul>}
          <div className="mt-3"><Button onClick={() => onPrefill(result.prefill)}>Review details</Button></div>
        </div>
      )}
      <UnavailableNotice title="How autofill works">DOCKET fetches the page from our server and reads only the job details the page publishes for search engines. Many job boards (LinkedIn among them) require a login or block automated access, and then nothing can be filled in. Nothing is saved until you review and confirm.</UnavailableNotice>
    </div>
  );
}

const TABS = [['link', 'From job link'], ['manual', 'Manual entry'], ['quick', 'Quick add']];

export default function AddApplication() {
  const { id } = useParams(); const navigate = useNavigate(); const [sp] = useSearchParams();
  const [tab, setTab] = useState(sp.get('mode') === 'link' ? 'link' : 'manual');
  const [prefill, setPrefill] = useState(null);
  const rec = useAsync(() => (id ? applicationService.get(id) : Promise.resolve(null)), [id]);
  const done = (saved) => navigate(`/app/applications/${saved.id}`);

  if (id) {
    if (rec.loading) return <SkeletonRows />;
    if (rec.error) return <ErrorState error={rec.error} onRetry={rec.reload} />;
    return (<><PageHeader title="Edit application" /><FullForm id={id} initial={fromRecord(rec.data)} onSaved={done} /></>);
  }
  return (
    <>
      <PageHeader title="Add application" description="Use a job link, fill in everything, or save the basics now and finish later." />
      <div role="tablist" aria-label="Entry method" className="mb-5 inline-flex rounded-control border border-border bg-surface-muted p-1">
        {TABS.map(([k, l]) => <button key={k} role="tab" aria-selected={tab === k} onClick={() => setTab(k)} className={`rounded-[7px] px-4 py-1.5 text-sm transition-colors ${tab === k ? 'bg-surface font-medium shadow-card' : 'text-fg-muted hover:text-fg'}`}>{l}</button>)}
      </div>
      {tab === 'link' && !prefill && <div className="card p-5"><LinkAutofill onPrefill={(d) => { setPrefill(d); }} /></div>}
      {tab === 'link' && prefill && <><p className="mb-3 text-sm text-fg-muted">Review the retrieved details below.</p><FullForm initial={prefill} draftKey="autofill" onSaved={done} /></>}
      {tab === 'manual' && <FullForm onSaved={done} />}
      {tab === 'quick' && <div className="card p-5"><QuickAdd onSaved={done} /></div>}
    </>
  );
}
