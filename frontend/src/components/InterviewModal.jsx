import { useEffect, useState } from 'react';
import { Modal } from './ui/Modal';
import { Button } from './ui/Button';
import { Input, Select, Textarea } from './ui/Field';
import { interviewService } from '../services/workspace';
import { apiError } from '../services/api';
import { useToast } from '../context/ToastContext';
import { INTERVIEW_MODES, INTERVIEW_RESULTS, ROUND_TYPES } from '../config/constants';

const isoToLocal = (iso) => { if (!iso) return ''; const d = new Date(iso); const p = (n) => String(n).padStart(2, '0'); return `${d.getFullYear()}-${p(d.getMonth() + 1)}-${p(d.getDate())}T${p(d.getHours())}:${p(d.getMinutes())}`; };
const EMPTY = { interviewDate: '', round: 'Technical', interviewMode: 'Video call', meetingLink: '', preparationNotes: '', topicsToRevise: '', questionsToAsk: '', result: 'Pending', feedback: '', followUpDate: '' };

export default function InterviewModal({ open, onClose, appId, interview, onSaved }) {
  const toast = useToast(); const [v, setV] = useState(EMPTY); const [saving, setSaving] = useState(false); const [err, setErr] = useState({});
  useEffect(() => { if (open) { setV(interview ? { ...EMPTY, ...Object.fromEntries(Object.entries(interview).map(([k, x]) => [k, x ?? ''])), interviewDate: isoToLocal(interview.interviewDate), followUpDate: interview.followUpDate?.slice(0, 10) || '' } : EMPTY); setErr({}); } }, [open, interview]);
  const set = (k) => (e) => setV((s) => ({ ...s, [k]: e.target.value }));
  const save = async () => {
    if (!v.interviewDate) return setErr({ interviewDate: 'Choose a date and time' });
    if (v.meetingLink && !/^https?:\/\//.test(v.meetingLink)) return setErr({ meetingLink: 'Enter a full link starting with http(s)://' });
    setSaving(true);
    try { const body = { ...v, interviewDate: new Date(v.interviewDate).toISOString(), followUpDate: v.followUpDate || null }; interview ? await interviewService.update(interview.id, body) : await interviewService.create(appId, body); toast.success('Interview saved.'); onSaved(); onClose(); }
    catch (e) { toast.error(apiError(e).message); } finally { setSaving(false); }
  };
  return (
    <Modal open={open} onClose={onClose} title={interview ? 'Edit interview' : 'Add interview round'} size="lg"
      footer={<><Button variant="secondary" onClick={onClose}>Cancel</Button><Button loading={saving} onClick={save}>Save interview</Button></>}>
      <div className="grid gap-4 sm:grid-cols-2">
        <Input label="Date and time" type="datetime-local" required value={v.interviewDate} onChange={set('interviewDate')} error={err.interviewDate} />
        <Select label="Round" options={ROUND_TYPES} value={v.round} onChange={set('round')} />
        <Select label="Mode" options={INTERVIEW_MODES} value={v.interviewMode} onChange={set('interviewMode')} />
        <Input label="Meeting link" placeholder="https://" value={v.meetingLink} onChange={set('meetingLink')} error={err.meetingLink} />
        <div className="sm:col-span-2"><Textarea label="Preparation notes" rows={3} value={v.preparationNotes} onChange={set('preparationNotes')} /></div>
        <Textarea label="Topics to revise" rows={3} value={v.topicsToRevise} onChange={set('topicsToRevise')} />
        <Textarea label="Questions to ask" rows={3} value={v.questionsToAsk} onChange={set('questionsToAsk')} />
        <Select label="Outcome" options={INTERVIEW_RESULTS} value={v.result} onChange={set('result')} />
        <Input label="Follow-up date" type="date" value={v.followUpDate} onChange={set('followUpDate')} />
        <div className="sm:col-span-2"><Textarea label="Feedback" rows={3} value={v.feedback} onChange={set('feedback')} /></div>
      </div>
    </Modal>
  );
}
