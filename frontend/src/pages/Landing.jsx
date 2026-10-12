import { Link } from 'react-router-dom';
import { motion } from 'motion/react';
import { Briefcase, CalendarClock, ListChecks, Gauge, BellRing, ShieldCheck, Columns3, BarChart3, Check } from 'lucide-react';
import { Button } from '../components/ui/Button';
import { StatusBadge } from '../components/ui/Display';
import { STATUSES } from '../config/constants';

const ease = [0.22, 1, 0.36, 1];

/** Illustrative composition only. Nothing here is real or persisted data. */
function DocketIllustration() {
  const rows = ['Applied', 'Shortlisted', 'Assessment', 'Interview', 'Selected'];
  return (
    <motion.figure initial={{ opacity: 0, y: 24 }} animate={{ opacity: 1, y: 0 }} transition={{ duration: 0.7, delay: 0.25, ease }} className="relative mx-auto w-full max-w-md" aria-label="Illustration of a docket sheet moving through application stages">
      <motion.div animate={{ y: [0, -6, 0] }} transition={{ duration: 7, repeat: Infinity, ease: 'easeInOut' }} className="card relative p-6 shadow-pop">
        <div className="mb-5 flex items-center justify-between border-b border-dashed border-border-strong pb-4">
          <div><div className="h-3 w-32 rounded-full bg-fg/80" /><div className="mt-2 h-2.5 w-20 rounded-full bg-border-strong" /></div>
          <div className="h-9 w-9 rounded-control bg-primary-soft" />
        </div>
        <ul className="space-y-3.5">
          {rows.map((s, i) => (
            <motion.li key={s} initial={{ opacity: 0, x: -10 }} animate={{ opacity: 1, x: 0 }} transition={{ delay: 0.6 + i * 0.12, duration: 0.4, ease }} className="flex items-center gap-3">
              <span className={`flex h-5 w-5 items-center justify-center rounded-full border ${i < 3 ? 'border-primary bg-primary text-on-primary' : 'border-border-strong'}`}>{i < 3 && <Check size={12} strokeWidth={3} />}</span>
              <span className="h-px flex-1 bg-border" />
              <StatusBadge status={s} />
            </motion.li>
          ))}
        </ul>
      </motion.div>
      <figcaption className="mt-3 text-center text-xs text-fg-subtle">Illustration — your own docket starts empty.</figcaption>
    </motion.figure>
  );
}

const FEATURES = [
  { icon: Briefcase, t: 'One record per application', d: 'Company, role, deadline, source, notes, documents and status history in a single place.' },
  { icon: Columns3, t: 'List and Kanban views', d: 'Drag applications between stages. Every move is saved and added to the timeline.' },
  { icon: CalendarClock, t: 'Interview tracker', d: 'Log every round with its mode, link, topics, questions to ask and outcome.' },
  { icon: ListChecks, t: 'Two separate checklists', d: 'Interview preparation and job requirements stay apart so neither gets buried.' },
  { icon: Gauge, t: 'Skill match score', d: 'Matched required skills divided by total required skills. No guesswork, no AI.' },
  { icon: BarChart3, t: 'Analytics from your records', d: 'Funnel, sources and conversion rates with the denominator always shown.' },
];

const STEPS = [
  ['Add an application', 'Paste a job link, fill the full form, or Quick Add with just a company and role.'],
  ['Track what happens next', 'Move it through Applied, Shortlisted, Assessment, Interview and the outcome.'],
  ['Prepare and follow up', 'Schedule rounds, tick off preparation, and set follow-up dates.'],
  ['Act on what is urgent', 'The Needs Action Center ranks deadlines, follow-ups and interviews for you.'],
];

export default function Landing() {
  return (
    <>
      <section className="mx-auto grid max-w-6xl items-center gap-12 px-4 pb-20 pt-14 lg:grid-cols-2 lg:pt-24">
        <div>
          <motion.h1 initial={{ opacity: 0, y: 16 }} animate={{ opacity: 1, y: 0 }} transition={{ duration: 0.55, ease }} className="text-4xl font-bold leading-[1.05] sm:text-5xl lg:text-6xl">
            Your job search, finally organized.
          </motion.h1>
          <motion.p initial={{ opacity: 0, y: 16 }} animate={{ opacity: 1, y: 0 }} transition={{ duration: 0.55, delay: 0.1, ease }} className="mt-5 max-w-lg text-lg text-fg-muted">
            Track applications, manage interviews, watch deadlines and see how your search is really going — all from one workspace built for students and new graduates.
          </motion.p>
          <motion.div initial={{ opacity: 0, y: 16 }} animate={{ opacity: 1, y: 0 }} transition={{ duration: 0.55, delay: 0.2, ease }} className="mt-8 flex flex-wrap gap-3">
            <Link to="/register"><Button size="lg">Create free account</Button></Link>
            <a href="#features"><Button size="lg" variant="secondary">Explore features</Button></a>
          </motion.div>
        </div>
        <DocketIllustration />
      </section>

      <section id="features" className="scroll-mt-20 border-t border-border bg-surface py-20">
        <div className="mx-auto max-w-6xl px-4">
          <h2 className="max-w-xl text-3xl font-semibold">Everything a search needs, nothing it does not</h2>
          <div className="mt-10 grid gap-x-10 gap-y-9 sm:grid-cols-2 lg:grid-cols-3">
            {FEATURES.map(({ icon: Icon, t, d }, i) => (
              <motion.div key={t} initial={{ opacity: 0, y: 14 }} whileInView={{ opacity: 1, y: 0 }} viewport={{ once: true, margin: '-60px' }} transition={{ duration: 0.4, delay: (i % 3) * 0.07, ease }}>
                <Icon className="text-primary" size={22} /><h3 className="mt-3 text-base font-semibold">{t}</h3><p className="mt-1.5 text-sm text-fg-muted">{d}</p>
              </motion.div>
            ))}
          </div>
        </div>
      </section>

      <section id="how-it-works" className="scroll-mt-20 py-20">
        <div className="mx-auto max-w-6xl px-4">
          <h2 className="text-3xl font-semibold">How DOCKET works</h2>
          <ol className="mt-10 grid gap-6 md:grid-cols-4">
            {STEPS.map(([t, d], i) => (
              <li key={t} className="relative border-t-2 border-primary pt-4">
                <span className="font-display text-sm text-fg-subtle">Step {i + 1}</span><h3 className="mt-1 text-base font-semibold">{t}</h3><p className="mt-1.5 text-sm text-fg-muted">{d}</p>
              </li>
            ))}
          </ol>
          <div className="mt-12 flex flex-wrap items-center gap-2" aria-label="Application workflow stages">
            {STATUSES.slice(0, 5).map((s, i) => (<span key={s} className="flex items-center gap-2"><StatusBadge status={s} />{i < 4 && <span className="h-px w-6 bg-border-strong" />}</span>))}
            <span className="text-sm text-fg-muted">— or Rejected / Withdrawn at any point.</span>
          </div>
        </div>
      </section>

      <section className="border-t border-border bg-surface py-20">
        <div className="mx-auto grid max-w-6xl gap-12 px-4 md:grid-cols-2">
          <div><BellRing className="text-primary" /><h2 className="mt-3 text-2xl font-semibold">Deadlines and follow-ups that find you</h2>
            <p className="mt-2 text-fg-muted">Set reminders for deadlines, interviews and follow-ups. Priority is scored with a published formula, and every score explains itself.</p></div>
          <div><Gauge className="text-primary" /><h2 className="mt-3 text-2xl font-semibold">Know where you stand</h2>
            <p className="mt-2 text-fg-muted">Add your skills once. Every application shows which required skills you have, which you are missing, and what to review.</p></div>
        </div>
      </section>

      <section id="privacy" className="scroll-mt-20 py-20">
        <div className="mx-auto max-w-6xl px-4">
          <ShieldCheck className="text-primary" /><h2 className="mt-3 max-w-xl text-3xl font-semibold">Private by design</h2>
          <ul className="mt-6 grid gap-3 text-sm text-fg-muted sm:grid-cols-2">
            {['Passwords are hashed with BCrypt and never stored in plain text.', 'Your records are visible only to your account.', 'Share links are read-only, expire, and can be revoked at any time.', 'Shared pages show summary counts only — never notes, documents or contacts.'].map((x) => (
              <li key={x} className="flex gap-2"><Check size={16} className="mt-0.5 shrink-0 text-success" />{x}</li>))}
          </ul>
        </div>
      </section>

      <section className="border-t border-border bg-primary py-16 text-on-primary">
        <div className="mx-auto flex max-w-6xl flex-col items-start justify-between gap-6 px-4 sm:flex-row sm:items-center">
          <h2 className="max-w-md text-3xl font-semibold">Start your docket today.</h2>
          <Link to="/register"><Button size="lg" variant="secondary">Create free account</Button></Link>
        </div>
      </section>
    </>
  );
}
