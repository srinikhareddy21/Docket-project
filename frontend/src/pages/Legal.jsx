import { useParams } from 'react-router-dom';
import { UnavailableNotice } from '../components/ui/Display';

/** Placeholder the project owner must replace before going live. */
const P = ({ children }) => <strong className="font-medium">[{children}]</strong>;

const PRIVACY = [
  ['Who we are', <>DOCKET is operated by <P>OWNER: legal name of operator</P>, <P>OWNER: postal address</P>. For any privacy question, contact <P>OWNER: privacy contact email</P>.</>],
  ['Information you give us', <>
    <p>When you register we collect your name, email address and a password. Your password is stored only as a one-way bcrypt hash; we cannot read it.</p>
    <p>When you use DOCKET you choose to store: job and internship application records (company, role, job type, work mode, location, salary or stipend, job description, required skills, status and status history, source, dates, application link, follow-up dates, resume version, recruiter details and notes), your skills, interview rounds and details, checklist items, weekly goals, message templates, reminders and reminder preferences, and documents you upload (PDF, Word, PNG or JPEG).</p>
  </>],
  ['How we use it', <p>Only to provide DOCKET's features to you: showing your applications, dashboard, board, calendar and analytics; matching your skills against job requirements; sending reminders and account emails (such as password reset and email-change confirmation); and keeping your account secure. We do not sell your information and we do not use it for advertising.</p>],
  ['Job-link autofill', <p>If you paste a job link and ask DOCKET to fill in details, our server fetches that one public page on your behalf and extracts details for you to review. The website you link to will see a request from our server, not from your browser.</p>],
  ['Chrome browser extension', <>
    <p>If you install the optional DOCKET Chrome extension and connect it with a personal access token, the extension runs only when you open it. It can read structured job details from the active tab (for example schema.org JobPosting or visible page content) so you can review them. Nothing is sent to DOCKET until you click Save. When you save, the extension sends the job fields you confirmed to our server using your token so the application is stored in your account.</p>
    <p>Tokens are stored as irreversible hashes on our servers; the full token is shown only once at creation. You can revoke a token at any time in Settings. The extension does not access browsing history, cookies, or passwords, and does not run in the background on every page.</p>
  </>],
  ['Third-party services', <>
    <p><strong className="font-medium">Email.</strong> Account and reminder emails are sent through an email (SMTP) provider: <P>OWNER: name of email provider</P>. It receives your email address and the message content.</p>
    <p><strong className="font-medium">Google Calendar (optional).</strong> Only if you connect it, DOCKET requests permission to manage calendar events and writes your interview and deadline events to your calendar. The Google access token is stored encrypted (AES-256-GCM). You can disconnect at any time in Settings, which revokes access. Google's use of data is governed by Google's own privacy policy.</p>
    <p><strong className="font-medium">Browser extension (optional).</strong> If you create an extension token, the extension can send job details to your account using that token. You can revoke tokens in Settings.</p>
    <p><strong className="font-medium">Share links (optional).</strong> If you create a share link, anyone with that link can view the progress summary it shows until it expires or you revoke it.</p>
    <p><strong className="font-medium">Hosting.</strong> Data is stored in a database and file storage operated by <P>OWNER: hosting provider and region</P>.</p>
  </>],
  ['Cookies and local storage', <p>DOCKET uses strictly necessary cookies only: an HttpOnly access cookie (about 15 minutes), an HttpOnly refresh cookie (30 days if you choose "remember me", otherwise until the browser session ends) and a security token cookie that protects against cross-site request forgery. Unsaved form drafts may be kept in your browser's local storage on your device. We do not use analytics or advertising cookies.</p>],
  ['Security', <p>We use password hashing, HttpOnly cookies, CSRF protection, rate limiting on sign-in, checks on uploaded file types, and encryption for stored third-party tokens. Each account can only access its own records. No system is perfectly secure, and we cannot guarantee absolute security.</p>],
  ['Retention and deletion', <p>Your data is kept while your account exists. Applications you delete are kept for a recovery period (<P>OWNER: confirm period, default 30 days</P>) and then permanently removed. You can delete your account in Settings; this permanently deletes your records and uploaded files. Backups may retain copies for <P>OWNER: backup retention period</P>.</p>],
  ['Your choices and rights', <p>You can view, edit and delete your information in the app, export your applications as CSV, change your email address, disconnect integrations and delete your account. Depending on where you live you may have further rights (for example to access, correct or object to processing). Contact <P>OWNER: privacy contact email</P> to exercise them. <P>LEGAL REVIEW: applicable laws, e.g. GDPR or India DPDP Act</P></p>],
  ['Children', <p>DOCKET is not intended for children under <P>OWNER: minimum age</P>.</p>],
  ['Changes to this policy', <p>We may update this policy. We will show the new date below and, for significant changes, notify you by email or in the app.</p>],
];

const TERMS = [
  ['About these terms', <p>These terms govern your use of DOCKET, operated by <P>OWNER: legal name of operator</P>. By creating an account you agree to them. If you do not agree, do not use DOCKET.</p>],
  ['Your account', <p>Provide accurate registration details and keep your password confidential. You are responsible for activity under your account. Tell us promptly at <P>OWNER: contact email</P> if you believe your account has been accessed without permission. You must be at least <P>OWNER: minimum age</P> to use DOCKET.</p>],
  ['Acceptable use', <>
    <p>Use DOCKET only to manage your own job search. You must not:</p>
    <ul className="list-disc space-y-1 pl-5">
      <li>access or attempt to access other users' data or accounts;</li>
      <li>upload malware or content that is unlawful, infringing or that you have no right to store;</li>
      <li>probe, overload or disrupt the service, or bypass its security or rate limits;</li>
      <li>use autofill, share links or the extension to scrape sites, send spam or harass anyone;</li>
      <li>resell or provide the service to others without permission.</li>
    </ul>
  </>],
  ['Your content', <p>You keep ownership of the information and files you store. You are responsible for their accuracy, including application statuses, dates and deadlines. DOCKET does not submit applications to employers and does not verify any job, employer or recruiter. You allow us to store and process your content only to provide the service to you.</p>],
  ['Autofill and integrations', <p>Job-link autofill reads public pages and may miss, misread or fail to retrieve details, as websites change often. Always review autofilled information. Google Calendar, email delivery and the browser extension depend on third-party services that we do not control; they may be unavailable, delayed or changed, and their own terms apply. Reminders may not arrive on time, so do not rely on DOCKET as your only record of important deadlines.</p>],
  ['Availability, suspension and termination', <p>We aim to keep DOCKET available but do not promise uninterrupted service, and we may change or discontinue features. You may delete your account at any time in Settings, which permanently deletes your data. We may suspend or close accounts that break these terms. <P>OWNER: notice period and data export window on termination</P></p>],
  ['Intellectual property', <p>DOCKET's software, design and branding belong to <P>OWNER: rights holder</P> and its licensors. These terms give you a limited, personal, non-transferable right to use the service.</p>],
  ['Disclaimers and limitation of liability', <p>DOCKET is provided "as is" and "as available". To the extent permitted by law, we are not liable for missed opportunities, deadlines or interviews, lost data, or indirect or consequential loss, and our total liability is limited to <P>LEGAL REVIEW: liability cap</P>. Nothing in these terms limits liability that cannot be limited by law.</p>],
  ['Governing law', <p>These terms are governed by the laws of <P>OWNER / LEGAL REVIEW: jurisdiction</P>, and disputes will be handled by the courts of <P>OWNER / LEGAL REVIEW: venue</P>.</p>],
  ['Changes and contact', <p>We may update these terms. We will show the new date below and notify you of significant changes; continuing to use DOCKET after that means you accept them. Questions: <P>OWNER: contact email</P>.</p>],
];

export default function Legal() {
  const { doc } = useParams();
  const isTerms = doc === 'terms';
  const title = isTerms ? 'Terms of use' : 'Privacy policy';
  const sections = isTerms ? TERMS : PRIVACY;
  return (
    <div className="mx-auto max-w-2xl px-4 py-16">
      <h1 className="text-3xl font-semibold">{title}</h1>
      <p className="mt-2 text-sm text-fg-muted">Last updated: <P>OWNER: date</P></p>
      <div className="mt-6"><UnavailableNotice title="Draft — not yet final">This {title.toLowerCase()} is a draft. Items in [brackets] must be completed by the owner, and the full text should be legally reviewed before DOCKET goes live.</UnavailableNotice></div>
      <div className="mt-10 space-y-8">
        {sections.map(([heading, body], i) => (
          <section key={heading} aria-labelledby={`s${i}`}>
            <h2 id={`s${i}`} className="text-lg font-semibold">{i + 1}. {heading}</h2>
            <div className="mt-2 space-y-3 leading-relaxed text-fg-muted">{body}</div>
          </section>
        ))}
      </div>
    </div>
  );
}
