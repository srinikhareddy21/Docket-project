import { useState } from 'react';
import { Link, useNavigate, useParams } from 'react-router-dom';
import { ExternalLink, Pencil, Plus, Trash2, Mail } from 'lucide-react';
import { AnimatePresence, motion } from 'motion/react';
import { applicationService } from '../services/applications';
import { checklistService, interviewService, skillService } from '../services/workspace';
import { apiError } from '../services/api';
import { useAsync } from '../hooks/useAsync';
import { useToast } from '../context/ToastContext';
import { Button } from '../components/ui/Button';
import { Card, ErrorState, PriorityBadge, ProgressBar, SkeletonRows, StatusBadge } from '../components/ui/Display';
import { ConfirmDialog } from '../components/ui/Modal';
import Checklist from '../components/Checklist';
import Documents from '../components/Documents';
import InterviewModal from '../components/InterviewModal';
import { REQUIREMENT_CATEGORIES, STATUSES } from '../config/constants';
import { formatDate, formatDateTime } from '../utils/format';
import { matchSkills } from '../utils/skills';

function Row({ k, v }) { return v ? <div><dt className="text-xs text-fg-muted">{k}</dt><dd className="mt-0.5 break-words text-sm">{v}</dd></div> : null; }

export default function ApplicationDetail() {
  const { id } = useParams(); const navigate = useNavigate(); const toast = useToast();
  const app = useAsync(() => applicationService.get(id), [id]);
  const hist = useAsync(() => applicationService.history(id), [id]);
  const ints = useAsync(() => interviewService.list({ applicationId: id }), [id]);
  const list = useAsync(() => checklistService.list(id), [id]);
  const skills = useAsync(() => skillService.list(), []);
  const [modal, setModal] = useState({ open: false, interview: null });
  const [delInt, setDelInt] = useState(null); const [confirmDel, setConfirmDel] = useState(false); const [deleting, setDeleting] = useState(false);
  const [note, setNote] = useState(''); const [expanded, setExpanded] = useState(true);

  if (app.loading) return <SkeletonRows rows={6} />;
  if (app.error) return <ErrorState error={app.error} onRetry={app.reload} />;
  const a = app.data;
  const match = matchSkills((skills.data || []).map((s) => s.name), a.requiredSkills || []);

  const setStatus = async (status) => {
    try { await applicationService.setStatus(id, status, note || undefined); setNote(''); toast.success(`Status changed to ${status}.`); app.reload(); hist.reload(); }
    catch (e) { toast.error(apiError(e).message); }
  };
  const setFollowUp = async (value) => {
    try { await applicationService.update(id, { ...a, followUpDate: value || null }); toast.success('Follow-up date saved.'); app.reload(); } catch (e) { toast.error(apiError(e).message); }
  };
  const doDelete = async () => {
    setDeleting(true);
    try {
      await applicationService.remove(id); setConfirmDel(false); navigate('/app/applications');
      toast.info(`${a.companyName} deleted.`, { duration: 10000, action: { label: 'Undo', onClick: async () => {
        try { await applicationService.restore(id); toast.success('Application restored.'); navigate(`/app/applications/${id}`); } catch (e) { toast.error(`Could not restore: ${apiError(e).message}`); } } } });
    } catch (e) { toast.error(apiError(e).message); setDeleting(false); }
  };
  const removeInterview = async () => {
    try { await interviewService.remove(delInt.id); toast.success('Interview removed.'); setDelInt(null); ints.reload(); } catch (e) { toast.error(apiError(e).message); }
  };

  return (
    <>
      <div className="mb-6 flex flex-wrap items-start justify-between gap-3">
        <div className="min-w-0">
          <Link to="/app/applications" className="text-sm text-fg-muted hover:underline">Applications</Link>
          <h1 className="mt-1 break-words text-2xl font-semibold sm:text-3xl">{a.position}</h1>
          <p className="text-fg-muted">{a.companyName}{a.location ? ` · ${a.location}` : ''}</p>
        </div>
        <div className="flex flex-wrap items-center gap-2">
          {a.applicationLink && <a href={a.applicationLink} target="_blank" rel="noopener noreferrer"><Button variant="secondary" icon={ExternalLink}>Job posting</Button></a>}
          <Link to={`/app/templates?company=${encodeURIComponent(a.companyName)}&role=${encodeURIComponent(a.position)}`}><Button variant="secondary" icon={Mail}>Message</Button></Link>
          <Link to={`/app/applications/${id}/edit`}><Button variant="secondary" icon={Pencil}>Edit</Button></Link>
          <Button variant="danger" icon={Trash2} onClick={() => setConfirmDel(true)}>Delete</Button>
        </div>
      </div>

      <div className="grid gap-4 lg:grid-cols-3">
        <div className="space-y-4 lg:col-span-2">
          <Card title="Details">
            <dl className="grid gap-4 sm:grid-cols-2">
              <Row k="Job type" v={a.jobType} /><Row k="Work mode" v={a.workMode} /><Row k="Source" v={a.applicationSource} />
              <Row k="Applied" v={a.applicationDate && formatDate(a.applicationDate)} /><Row k="Deadline" v={a.deadline && formatDateTime(a.deadline)} />
              <Row k="Salary or stipend" v={a.salaryOrStipend} /><Row k="Recruiter or referral" v={a.recruiterDetails} /><Row k="Resume version" v={a.resumeVersion} />
            </dl>
            {a.jobDescription && <div className="mt-4 border-t border-border pt-4"><h3 className="mb-1 text-xs text-fg-muted">Job description</h3><p className="whitespace-pre-wrap break-words text-sm">{a.jobDescription}</p></div>}
            {a.notes && <div className="mt-4 border-t border-border pt-4"><h3 className="mb-1 text-xs text-fg-muted">Notes</h3><p className="whitespace-pre-wrap break-words text-sm">{a.notes}</p></div>}
          </Card>

          <Card title="Interviews" action={<Button size="sm" variant="secondary" icon={Plus} onClick={() => setModal({ open: true, interview: null })}>Add round</Button>}>
            {ints.loading ? <SkeletonRows rows={2} /> : ints.error ? <ErrorState error={ints.error} onRetry={ints.reload} /> : ints.data?.length ? (
              <ul className="divide-y divide-border">{ints.data.map((i) => (
                <li key={i.id} className="flex items-start justify-between gap-3 py-3 first:pt-0 last:pb-0">
                  <div><p className="text-sm font-medium">{i.round} · {i.interviewMode}</p><p className="text-sm text-fg-muted">{formatDateTime(i.interviewDate)} · {i.result}</p>
                    {i.meetingLink && <a className="text-sm underline" href={i.meetingLink} target="_blank" rel="noopener noreferrer">Meeting link</a>}</div>
                  <div className="flex gap-1"><Button size="sm" variant="ghost" onClick={() => setModal({ open: true, interview: i })}>Edit</Button><Button size="sm" variant="ghost" onClick={() => setDelInt(i)}>Remove</Button></div>
                </li>))}</ul>
            ) : (
              <div className="space-y-2">
                <p className="text-sm text-fg-muted">
                  {a.currentStatus === 'Interview'
                    ? 'Status is Interview, but no date or time is recorded yet. Add a round so the dashboard, calendar, priority and reminders can use it.'
                    : 'No interview rounds yet.'}
                </p>
                {a.currentStatus === 'Interview' && (
                  <Button size="sm" icon={Plus} onClick={() => setModal({ open: true, interview: null })}>Add interview details</Button>
                )}
              </div>
            )}
          </Card>

          <div className="grid gap-4 md:grid-cols-2">
            <Card>{list.loading ? <SkeletonRows rows={2} /> : <Checklist appId={id} type="PREP" title="Interview preparation" items={list.data || []} onChanged={list.reload} />}</Card>
            <Card>{list.loading ? <SkeletonRows rows={2} /> : <Checklist appId={id} type="REQUIREMENT" title="Job requirements" categories={REQUIREMENT_CATEGORIES} items={list.data || []} onChanged={list.reload} />}</Card>
          </div>

          <Card title="Documents"><Documents appId={id} /></Card>
        </div>

        <div className="space-y-4">
          <Card title="Status">
            <div className="mb-3"><StatusBadge status={a.currentStatus} /></div>
            <label className="label" htmlFor="st">Change status</label>
            <select id="st" className="field" value={a.currentStatus} onChange={(e) => setStatus(e.target.value)}>{STATUSES.map((s) => <option key={s}>{s}</option>)}</select>
            <input aria-label="Optional note for the next status change" className="field mt-2" placeholder="Optional note for this change" value={note} onChange={(e) => setNote(e.target.value)} />
          </Card>

          <Card title="Priority">
            {a.priority ? <><div className="mb-2 flex items-center gap-2"><PriorityBadge level={a.priority.level} /><span className="text-xs text-fg-muted">score {a.priority.score}/100</span></div>
              <ul className="list-disc space-y-1 pl-4 text-sm text-fg-muted">{a.priority.reasons.map((r) => <li key={r}>{r}</li>)}</ul></> : <p className="text-sm text-fg-muted">Not calculated.</p>}
          </Card>

          <Card title="Skill match">
            {match.score === null ? <p className="text-sm text-fg-muted">Not enough skill data. Add required skills to this application.</p> : (
              <>
                <p className="font-display text-3xl font-semibold">{Math.round(match.score * 10) / 10}%</p>
                <p className="mb-2 text-xs text-fg-muted">{match.matched.length} of {match.total} required skills matched</p>
                <ProgressBar value={match.score} label="Skill match" />
                {match.matched.length > 0 && <p className="mt-3 text-sm"><span className="font-medium">Matched:</span> <span className="text-fg-muted">{match.matched.join(', ')}</span></p>}
                {match.missing.length > 0 && <p className="mt-2 text-sm"><span className="font-medium">Missing:</span> <span className="text-fg-muted">{match.missing.join(', ')}</span></p>}
                {match.missing.length > 0 && <p className="mt-2 text-xs text-fg-subtle">Review the missing skills before your next round.</p>}
                {!(skills.data || []).length && <p className="mt-2 text-xs text-fg-subtle">You have not added any skills yet. <Link to="/app/skills" className="underline">Add your skills</Link>.</p>}
              </>)}
          </Card>

          <Card title="Follow-up">
            <label className="label" htmlFor="fu">Follow-up date</label>
            <input id="fu" type="date" className="field" defaultValue={a.followUpDate?.slice(0, 10) || ''} onBlur={(e) => e.target.value !== (a.followUpDate?.slice(0, 10) || '') && setFollowUp(e.target.value)} />
          </Card>

          <Card title="Status history" action={<button className="text-xs text-fg-muted underline" onClick={() => setExpanded((x) => !x)} aria-expanded={expanded}>{expanded ? 'Collapse' : 'Expand'}</button>}>
            <AnimatePresence initial={false}>{expanded && (
              <motion.div initial={{ height: 0, opacity: 0 }} animate={{ height: 'auto', opacity: 1 }} exit={{ height: 0, opacity: 0 }} className="overflow-hidden">
                {hist.loading ? <SkeletonRows rows={2} /> : hist.data?.length ? (
                  <ol className="relative space-y-4 border-l border-border-strong pl-4">{hist.data.map((h) => (
                    <li key={h.id} className="relative"><span className="absolute -left-[21px] top-1.5 h-2 w-2 rounded-full bg-primary" />
                      <p className="text-sm">{h.previousStatus ? `${h.previousStatus} → ` : ''}<span className="font-medium">{h.newStatus}</span></p>
                      <p className="text-xs text-fg-subtle">{formatDateTime(h.changedAt)}</p>{h.note && <p className="mt-0.5 text-sm text-fg-muted">{h.note}</p>}</li>))}</ol>
                ) : <p className="text-sm text-fg-muted">No history yet.</p>}
              </motion.div>)}</AnimatePresence>
          </Card>
        </div>
      </div>

      <InterviewModal open={modal.open} interview={modal.interview} appId={id} onClose={() => setModal({ open: false, interview: null })} onSaved={() => { ints.reload(); app.reload(); }} />
      <ConfirmDialog open={!!delInt} onClose={() => setDelInt(null)} onConfirm={removeInterview} danger title="Remove interview?" message="This interview round and its notes will be removed." confirmLabel="Remove" />
      <ConfirmDialog open={confirmDel} onClose={() => setConfirmDel(false)} onConfirm={doDelete} danger loading={deleting} title="Delete this application?" message="You can undo the deletion for a short time after confirming." confirmLabel="Delete" />
    </>
  );
}
