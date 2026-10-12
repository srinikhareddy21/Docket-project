import { useState } from 'react';
import { Sparkles, X } from 'lucide-react';
import { skillService } from '../services/workspace';
import { apiError } from '../services/api';
import { useAsync } from '../hooks/useAsync';
import { useToast } from '../context/ToastContext';
import { Button } from '../components/ui/Button';
import { Card, EmptyState, ErrorState, PageHeader, SkeletonRows } from '../components/ui/Display';
import { SKILL_CATEGORIES } from '../config/constants';

export default function Skills() {
  const toast = useToast();
  const { data, loading, error, reload } = useAsync(() => skillService.list(), []);
  const [name, setName] = useState(''); const [category, setCategory] = useState(SKILL_CATEGORIES[0]); const [busy, setBusy] = useState(false);
  const add = async (e) => {
    e.preventDefault(); if (!name.trim()) return; setBusy(true);
    try { await skillService.add({ name: name.trim(), category }); setName(''); reload(); }
    catch (er) { toast.error(apiError(er).status === 409 ? 'You already have that skill.' : apiError(er).message); } finally { setBusy(false); }
  };
  const remove = async (s) => { try { await skillService.remove(s.id); reload(); } catch (er) { toast.error(apiError(er).message); } };
  return (
    <>
      <PageHeader title="My skills" description="Your skills are compared with each application's required skills to calculate a match score." />
      <form onSubmit={add} className="card mb-4 flex flex-wrap items-end gap-3 p-4">
        <div className="min-w-[200px] flex-1"><label className="label" htmlFor="sk">Skill</label><input id="sk" className="field" value={name} onChange={(e) => setName(e.target.value)} placeholder="e.g. Java" /></div>
        <div><label className="label" htmlFor="sc">Category</label><select id="sc" className="field" value={category} onChange={(e) => setCategory(e.target.value)}>{SKILL_CATEGORIES.map((c) => <option key={c}>{c}</option>)}</select></div>
        <Button type="submit" loading={busy}>Add skill</Button>
      </form>
      {loading && <SkeletonRows rows={3} />}
      {error && <ErrorState error={error} onRetry={reload} />}
      {data?.length === 0 && <EmptyState icon={Sparkles} title="No skills added" description="Add the skills you have. Nothing is added automatically." />}
      {data?.length > 0 && <div className="grid gap-4 md:grid-cols-2">{SKILL_CATEGORIES.map((c) => { const rows = data.filter((s) => s.category === c); if (!rows.length) return null; return (
        <Card key={c} title={c}><ul className="flex flex-wrap gap-2">{rows.map((s) => <li key={s.id} className="flex items-center gap-1 rounded-full bg-primary-soft py-1 pl-3 pr-1.5 text-sm">{s.name}<button aria-label={`Remove ${s.name}`} onClick={() => remove(s)} className="rounded-full p-0.5 text-fg-muted hover:text-danger"><X size={14} /></button></li>)}</ul></Card>); })}</div>}
    </>
  );
}
