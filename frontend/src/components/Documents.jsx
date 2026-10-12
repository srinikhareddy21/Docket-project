import { useRef, useState } from 'react';
import { Download, FileText, Trash2, Upload } from 'lucide-react';
import { documentService } from '../services/workspace';
import { apiError } from '../services/api';
import { useAsync } from '../hooks/useAsync';
import { useToast } from '../context/ToastContext';
import { Button } from './ui/Button';
import { ErrorState, SkeletonRows } from './ui/Display';
import { DOCUMENT_TYPES } from '../config/constants';
import { formatDate } from '../utils/format';

const MAX = 5 * 1024 * 1024;
const ALLOWED = ['application/pdf', 'application/msword', 'application/vnd.openxmlformats-officedocument.wordprocessingml.document', 'image/png', 'image/jpeg'];

export default function Documents({ appId }) {
  const toast = useToast(); const input = useRef(null);
  const { data, loading, error, reload } = useAsync(() => documentService.list(appId), [appId]);
  const [type, setType] = useState('Resume'); const [busy, setBusy] = useState(false);

  const upload = async (file) => {
    if (!file) return;
    if (!ALLOWED.includes(file.type)) return toast.error('Upload a PDF, Word document, PNG or JPG.');
    if (file.size > MAX) return toast.error('Files must be 5 MB or smaller.');
    const fd = new FormData(); fd.append('file', file); fd.append('type', type); setBusy(true);
    try { await documentService.upload(appId, fd); toast.success('Document uploaded.'); reload(); }
    catch (e) { toast.error(apiError(e).message); } finally { setBusy(false); if (input.current) input.current.value = ''; }
  };
  const download = async (d) => {
    try { const r = await documentService.download(d.id); const url = URL.createObjectURL(r.data); const a = document.createElement('a'); a.href = url; a.download = d.name; a.click(); URL.revokeObjectURL(url); }
    catch (e) { toast.error(apiError(e).message); }
  };
  const remove = async (d) => { try { await documentService.remove(d.id); toast.success('Document removed.'); reload(); } catch (e) { toast.error(apiError(e).message); } };

  if (loading) return <SkeletonRows rows={2} />;
  if (error) return <ErrorState error={error} onRetry={reload} />;
  return (
    <div>
      {data?.length ? <ul className="mb-3 divide-y divide-border">{data.map((d) => (
        <li key={d.id} className="flex items-center gap-3 py-2 text-sm"><FileText size={16} className="text-fg-subtle" /><span className="min-w-0 flex-1 truncate">{d.name}</span><span className="text-xs text-fg-muted">{d.type} · {formatDate(d.uploadedAt)}</span>
          <button aria-label={`Download ${d.name}`} onClick={() => download(d)} className="text-fg-muted hover:text-fg"><Download size={15} /></button>
          <button aria-label={`Delete ${d.name}`} onClick={() => remove(d)} className="text-fg-muted hover:text-danger"><Trash2 size={15} /></button></li>))}</ul>
        : <p className="mb-3 text-sm text-fg-muted">No documents attached.</p>}
      <div className="flex flex-wrap items-center gap-2">
        <select aria-label="Document type" className="field w-auto" value={type} onChange={(e) => setType(e.target.value)}>{DOCUMENT_TYPES.map((t) => <option key={t}>{t}</option>)}</select>
        <input ref={input} type="file" hidden accept=".pdf,.doc,.docx,.png,.jpg,.jpeg" onChange={(e) => upload(e.target.files?.[0])} />
        <Button variant="secondary" icon={Upload} loading={busy} onClick={() => input.current?.click()}>Upload file</Button>
        <span className="text-xs text-fg-subtle">PDF, Word, PNG or JPG up to 5 MB</span>
      </div>
    </div>
  );
}
