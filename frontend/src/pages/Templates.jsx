import { useMemo, useState } from 'react';
import { useSearchParams } from 'react-router-dom';
import { Copy, Mail } from 'lucide-react';
import { useAuth } from '../context/AuthContext';
import { useToast } from '../context/ToastContext';
import { Button } from '../components/ui/Button';
import { Input, Textarea } from '../components/ui/Field';
import { PageHeader } from '../components/ui/Display';

const TEMPLATES = {
  'Application follow-up': { subject: 'Following up on my {role} application', body: 'Dear {recruiter},\n\nI applied for the {role} position at {company} and wanted to follow up on the status of my application. I remain very interested in the role and would be glad to share anything further that would help.\n\nThank you for your time.\n\nBest regards,\n{name}' },
  'Interview thank-you': { subject: 'Thank you — {role} interview', body: 'Dear {recruiter},\n\nThank you for taking the time to speak with me about the {role} position at {company}. I enjoyed learning more about the team and the work, and I am excited about the opportunity.\n\nBest regards,\n{name}' },
  'Request status update': { subject: 'Status of my {role} application', body: 'Dear {recruiter},\n\nCould you please let me know where my application for {role} at {company} currently stands, and whether there is anything else you need from me?\n\nThank you,\n{name}' },
  'Reply to interview invitation': { subject: 'Re: Interview invitation — {role}', body: 'Dear {recruiter},\n\nThank you for inviting me to interview for the {role} position at {company}. I am happy to confirm my availability. Please let me know if you need any additional information beforehand.\n\nBest regards,\n{name}' },
};
const fill = (t, v) => t.replace(/\{(\w+)\}/g, (_, k) => v[k] || `[${k}]`);

export default function Templates() {
  const { user } = useAuth(); const toast = useToast(); const [sp] = useSearchParams();
  const [key, setKey] = useState(Object.keys(TEMPLATES)[0]);
  const [v, setV] = useState({ company: sp.get('company') || '', role: sp.get('role') || '', recruiter: 'Hiring Team', name: user?.name || '' });
  const generated = useMemo(() => ({ subject: fill(TEMPLATES[key].subject, v), body: fill(TEMPLATES[key].body, v) }), [key, v]);
  const [edit, setEdit] = useState(null); // user-edited override
  const subject = edit?.subject ?? generated.subject; const body = edit?.body ?? generated.body;
  const set = (k) => (e) => { setEdit(null); setV((s) => ({ ...s, [k]: e.target.value })); };
  const copy = async () => { try { await navigator.clipboard.writeText(`Subject: ${subject}\n\n${body}`); toast.success('Message copied to clipboard.'); } catch { toast.error('Could not copy. Select the text and copy it manually.'); } };
  return (
    <>
      <PageHeader title="Message templates" description="Fill in the details, edit the text, then copy it or open it in your email app. DOCKET never sends messages for you." />
      <div className="mb-4 flex flex-wrap gap-2" role="tablist">{Object.keys(TEMPLATES).map((k) => <button key={k} role="tab" aria-selected={key === k} onClick={() => { setKey(k); setEdit(null); }} className={`rounded-full border px-3.5 py-1.5 text-sm transition-colors ${key === k ? 'border-primary bg-primary text-on-primary' : 'border-border-strong text-fg-muted hover:bg-surface-muted'}`}>{k}</button>)}</div>
      <div className="grid gap-4 lg:grid-cols-5">
        <div className="card space-y-3 p-4 lg:col-span-2"><Input label="Company" value={v.company} onChange={set('company')} /><Input label="Role" value={v.role} onChange={set('role')} /><Input label="Recipient" value={v.recruiter} onChange={set('recruiter')} /><Input label="Your name" value={v.name} onChange={set('name')} /></div>
        <div className="card space-y-3 p-4 lg:col-span-3">
          <Input label="Subject" value={subject} onChange={(e) => setEdit({ subject: e.target.value, body })} />
          <Textarea label="Message" rows={12} value={body} onChange={(e) => setEdit({ subject, body: e.target.value })} />
          <div className="flex flex-wrap gap-2"><Button icon={Copy} onClick={copy}>Copy message</Button>
            <a href={`mailto:?subject=${encodeURIComponent(subject)}&body=${encodeURIComponent(body)}`}><Button variant="secondary" icon={Mail}>Open in email app</Button></a></div>
        </div>
      </div>
    </>
  );
}
