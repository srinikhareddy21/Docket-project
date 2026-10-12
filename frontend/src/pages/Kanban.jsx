import { useEffect, useState } from 'react';
import { Link } from 'react-router-dom';
import { motion } from 'motion/react';
import { Columns3, PlusCircle } from 'lucide-react';
import { applicationService } from '../services/applications';
import { apiError } from '../services/api';
import { useAsync } from '../hooks/useAsync';
import { useToast } from '../context/ToastContext';
import { Button } from '../components/ui/Button';
import { EmptyState, ErrorState, PageHeader, PriorityBadge, Skeleton } from '../components/ui/Display';
import { STATUSES, STATUS_TOKEN } from '../config/constants';
import { formatDate } from '../utils/format';

export default function Kanban() {
  const toast = useToast();
  const { data, loading, error, reload } = useAsync(() => applicationService.list({ size: 500, sort: 'applicationDate,desc' }), []);
  const [cards, setCards] = useState([]);
  const [dragId, setDragId] = useState(null);
  const [over, setOver] = useState(null);
  useEffect(() => { if (data) setCards(data.content); }, [data]);

  const move = async (id, status) => {
    const card = cards.find((c) => c.id === id);
    if (!card || card.currentStatus === status) return;
    const prev = cards;
    setCards((cs) => cs.map((c) => (c.id === id ? { ...c, currentStatus: status } : c))); // optimistic
    try { await applicationService.setStatus(id, status); toast.success(`${card.companyName} moved to ${status}.`); }
    catch (e) { setCards(prev); toast.error(`${apiError(e).message} The card was returned to ${card.currentStatus}.`, { action: { label: 'Retry', onClick: () => move(id, status) } }); }
  };

  if (loading) return (<><PageHeader title="Kanban board" /><div className="flex gap-3 overflow-x-auto">{STATUSES.map((s) => <Skeleton key={s} className="h-64 w-64 shrink-0" />)}</div></>);
  if (error) return (<><PageHeader title="Kanban board" /><ErrorState error={error} onRetry={reload} /></>);
  if (!cards.length) return (<><PageHeader title="Kanban board" /><EmptyState icon={Columns3} title="Nothing to organize yet" description="Add an application and it appears in the Applied column. Drag cards between stages as things progress." actions={<Link to="/app/applications/new"><Button icon={PlusCircle}>Add application</Button></Link>} /></>);

  return (
    <>
      <PageHeader title="Kanban board" description="Drag a card to a new stage. Each move is saved and added to the application's history." />
      <div className="flex gap-3 overflow-x-auto pb-4">
        {STATUSES.map((status) => {
          const col = cards.filter((c) => c.currentStatus === status);
          const t = STATUS_TOKEN[status];
          return (
            <section key={status} aria-label={`${status} column`} onDragOver={(e) => { e.preventDefault(); setOver(status); }} onDragLeave={() => setOver(null)}
              onDrop={(e) => { e.preventDefault(); setOver(null); move(dragId || Number(e.dataTransfer.getData('text/plain')), status); setDragId(null); }}
              className={`w-64 shrink-0 rounded-card border p-2.5 transition-colors ${over === status ? 'border-primary bg-primary-soft/60' : 'border-border bg-surface-muted/50'}`}>
              <h2 className="mb-2 flex items-center justify-between px-1 text-sm font-semibold"><span className="flex items-center gap-2"><span className="h-2 w-2 rounded-full" style={{ background: `rgb(var(--${t}))` }} />{status}</span><span className="text-xs font-normal text-fg-muted">{col.length}</span></h2>
              <ul className="min-h-[60px] space-y-2">
                {col.map((c) => (
                  <motion.li layout key={c.id} draggable onDragStart={(e) => { setDragId(c.id); e.dataTransfer.setData('text/plain', String(c.id)); e.dataTransfer.effectAllowed = 'move'; }} onDragEnd={() => setDragId(null)}
                    className={`card cursor-grab p-3 active:cursor-grabbing ${dragId === c.id ? 'opacity-50' : ''}`}>
                    <Link to={`/app/applications/${c.id}`} className="block truncate text-sm font-medium hover:underline">{c.companyName}</Link>
                    <p className="truncate text-xs text-fg-muted">{c.position}</p>
                    <div className="mt-2 flex items-center justify-between text-xs text-fg-subtle"><span>{c.deadline ? `Due ${formatDate(c.deadline)}` : formatDate(c.applicationDate)}</span>{c.priority && <PriorityBadge level={c.priority.level} />}</div>
                    <label className="sr-only" htmlFor={`mv-${c.id}`}>Move {c.companyName} to</label>
                    <select id={`mv-${c.id}`} value={c.currentStatus} onChange={(e) => move(c.id, e.target.value)} className="field mt-2 h-8 py-0 text-xs">{STATUSES.map((s) => <option key={s}>{s}</option>)}</select>
                  </motion.li>))}
              </ul>
            </section>
          );
        })}
      </div>
    </>
  );
}
