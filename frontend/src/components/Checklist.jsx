import { useState } from 'react';
import { Plus, Trash2 } from 'lucide-react';
import { checklistService } from '../services/workspace';
import { apiError } from '../services/api';
import { useToast } from '../context/ToastContext';
import { Button } from './ui/Button';
import { ProgressBar } from './ui/Display';

/** Used for BOTH checklists, kept as separate lists by `type` (PREP vs REQUIREMENT). */
export default function Checklist({ appId, type, title, items, categories, onChanged }) {
  const toast = useToast();
  const [text, setText] = useState(''); const [category, setCategory] = useState(categories?.[0] || '');
  const [adding, setAdding] = useState(false);
  const mine = items.filter((i) => i.type === type);
  const done = mine.filter((i) => i.completed).length;
  const pctDone = mine.length ? (done / mine.length) * 100 : 0;

  const add = async (e) => {
    e.preventDefault(); if (!text.trim()) return; setAdding(true);
    try { await checklistService.add(appId, { type, category: categories ? category : null, text: text.trim() }); setText(''); onChanged(); }
    catch (er) { toast.error(apiError(er).message); } finally { setAdding(false); }
  };
  const toggle = async (i) => { try { await checklistService.toggle(i.id, !i.completed); onChanged(); } catch (er) { toast.error(apiError(er).message); } };
  const remove = async (i) => { try { await checklistService.remove(i.id); onChanged(); } catch (er) { toast.error(apiError(er).message); } };

  const groups = categories ? categories : [null];
  return (
    <div>
      <div className="mb-3 flex items-center justify-between"><h3 className="text-sm font-semibold">{title}</h3><span className="text-xs text-fg-muted">{done}/{mine.length} done</span></div>
      {mine.length > 0 && <ProgressBar value={pctDone} label={`${title} completion`} className="mb-3" />}
      {groups.map((g) => {
        const rows = mine.filter((i) => (g ? i.category === g : true)); if (!rows.length) return null;
        return (
          <div key={g || 'all'} className="mb-3">
            {g && <p className="mb-1 text-xs font-medium text-fg-muted">{g}</p>}
            <ul className="space-y-1">{rows.map((i) => (
              <li key={i.id} className="group flex items-center gap-2 rounded px-1 py-1 hover:bg-surface-muted/60">
                <input type="checkbox" id={`c-${i.id}`} checked={i.completed} onChange={() => toggle(i)} className="accent-[rgb(var(--color-primary))]" />
                <label htmlFor={`c-${i.id}`} className={`flex-1 text-sm ${i.completed ? 'text-fg-subtle line-through' : ''}`}>{i.text}</label>
                <button onClick={() => remove(i)} aria-label={`Delete ${i.text}`} className="text-fg-subtle opacity-0 transition-opacity hover:text-danger focus:opacity-100 group-hover:opacity-100"><Trash2 size={14} /></button>
              </li>))}</ul>
          </div>);
      })}
      {mine.length === 0 && <p className="mb-2 text-sm text-fg-muted">No items yet.</p>}
      <form onSubmit={add} className="flex flex-wrap gap-2">
        {categories && <select aria-label="Category" className="field w-auto" value={category} onChange={(e) => setCategory(e.target.value)}>{categories.map((c) => <option key={c}>{c}</option>)}</select>}
        <input aria-label={`Add to ${title}`} className="field min-w-[160px] flex-1" placeholder="Add an item" value={text} onChange={(e) => setText(e.target.value)} />
        <Button type="submit" variant="secondary" icon={Plus} loading={adding}>Add</Button>
      </form>
    </div>
  );
}
