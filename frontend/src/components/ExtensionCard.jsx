import { useState } from 'react';
import { Copy, KeyRound, Chrome, Download } from 'lucide-react';
import { extensionService } from '../services/workspace';
import { apiError } from '../services/api';
import { useAsync } from '../hooks/useAsync';
import { useToast } from '../context/ToastContext';
import { Button } from './ui/Button';
import { ConfirmDialog } from './ui/Modal';
import { Card, ErrorState, Skeleton } from './ui/Display';
import { formatDateTime } from '../utils/format';

/**
 * Personal access tokens for the Chrome extension.
 * Token is shown once after creation. Optional VITE_CHROME_STORE_URL enables "Add to Chrome".
 */
export default function ExtensionCard() {
  const toast = useToast();
  const { data, loading, error, reload } = useAsync(() => extensionService.list(), []);
  const [label, setLabel] = useState('');
  const [busy, setBusy] = useState(false);
  const [fresh, setFresh] = useState(null);
  const [revoking, setRevoking] = useState(null);

  const storeUrl = (import.meta.env.VITE_CHROME_STORE_URL || '').trim();
  const apiHint = typeof window !== 'undefined' ? window.location.origin : '';

  const create = async (e) => {
    e.preventDefault(); setBusy(true);
    try {
      const r = await extensionService.create(label.trim());
      setFresh(r.token);
      setLabel('');
      reload();
    } catch (err) {
      toast.error(apiError(err).message);
    } finally {
      setBusy(false);
    }
  };

  const copy = async () => {
    try {
      await navigator.clipboard.writeText(fresh);
      toast.success('Token copied.');
    } catch {
      toast.error('Could not copy. Select the token and copy it manually.');
    }
  };

  const copyApi = async () => {
    if (!apiHint) return;
    try {
      await navigator.clipboard.writeText(apiHint);
      toast.success('Server address copied.');
    } catch {
      toast.error('Could not copy.');
    }
  };

  const revoke = async () => {
    setBusy(true);
    try {
      await extensionService.revoke(revoking.id);
      toast.success('Token revoked. The extension using it can no longer save jobs.');
      setRevoking(null);
      reload();
    } catch (err) {
      toast.error(apiError(err).message);
    } finally {
      setBusy(false);
    }
  };

  return (
    <Card title="Browser extension">
      <div className="space-y-4">
        <p className="text-sm text-fg-muted">
          The DOCKET Chrome extension lets you save a job from the page you are viewing.
          You review every field before anything is sent to your account. The extension can only
          create applications and check for duplicates—it cannot read your existing data or change your password.
        </p>

        <div className="flex flex-wrap items-center gap-3">
          <a
            href="/downloads/docket-extension.zip"
            download="docket-extension.zip"
            className="inline-flex items-center gap-2 rounded-control bg-primary px-4 py-2 text-sm font-medium text-on-primary hover:bg-primary-hover"
          >
            <Download size={18} aria-hidden />
            Download Chrome Extension
          </a>
          {storeUrl ? (
            <a
              href={storeUrl}
              target="_blank"
              rel="noopener noreferrer"
              className="inline-flex items-center gap-2 rounded-control border border-border bg-surface px-4 py-2 text-sm font-medium text-fg hover:bg-surface-muted"
            >
              <Chrome size={18} aria-hidden />
              Add to Chrome
            </a>
          ) : null}
        </div>

        <div className="rounded-card border border-border bg-surface-muted/60 p-3 text-sm text-fg-muted">
          <p className="font-medium text-fg">Install from the ZIP</p>
          <ol className="mt-2 list-decimal space-y-1 pl-5">
            <li>Download the ZIP using the button above.</li>
            <li>Extract the ZIP to a folder on your computer.</li>
            <li>Open <code className="text-xs">chrome://extensions</code> in Chrome.</li>
            <li>Enable <strong className="font-medium text-fg">Developer mode</strong> (top right).</li>
            <li>Click <strong className="font-medium text-fg">Load unpacked</strong> and select the extracted folder that contains <code className="text-xs">manifest.json</code>.</li>
          </ol>
        </div>

        <ol className="list-decimal space-y-1 pl-5 text-sm text-fg-muted">
          <li>After installing, create a token below and copy it (shown only once).</li>
          <li>
            Open the extension’s settings and enter your DOCKET server address
            {apiHint ? (
              <>
                {' '}
                (<code className="text-xs break-all">{apiHint}</code>
                <button type="button" className="ml-1 text-xs underline" onClick={copyApi}>Copy</button>)
              </>
            ) : null}
            {' '}and the token, then test the connection.
          </li>
          <li>On a job page, open the extension, review the fields, and click Save to DOCKET.</li>
        </ol>

        <form onSubmit={create} className="flex gap-2">
          <label className="sr-only" htmlFor="xl">Token name</label>
          <input
            id="xl"
            className="field"
            maxLength={60}
            placeholder="Name, e.g. Work laptop"
            value={label}
            onChange={(e) => setLabel(e.target.value)}
          />
          <Button type="submit" icon={KeyRound} loading={busy}>Create token</Button>
        </form>

        {fresh && (
          <div role="status" className="rounded-card border border-border-strong bg-surface-muted p-3 text-sm">
            <p className="font-medium">Copy your token now. It is shown only once.</p>
            <div className="mt-2 flex items-center gap-2">
              <code className="min-w-0 flex-1 break-all rounded bg-surface px-2 py-1 text-xs">{fresh}</code>
              <Button size="sm" variant="secondary" icon={Copy} onClick={copy}>Copy</Button>
            </div>
            <button type="button" className="mt-2 text-xs text-fg-muted underline" onClick={() => setFresh(null)}>
              I have saved it
            </button>
          </div>
        )}

        {loading ? (
          <Skeleton className="h-16" />
        ) : error ? (
          <ErrorState error={error} onRetry={reload} />
        ) : data.length === 0 ? (
          <p className="text-sm text-fg-subtle">No tokens yet.</p>
        ) : (
          <ul className="divide-y divide-border text-sm">
            {data.map((t) => (
              <li key={t.id} className="flex items-center justify-between gap-3 py-2">
                <div className="min-w-0">
                  <p className="truncate font-medium">
                    {t.label} <span className="font-normal text-fg-subtle">…{t.hint}</span>
                  </p>
                  <p className="text-xs text-fg-subtle">
                    {t.state === 'ACTIVE'
                      ? `Expires ${formatDateTime(t.expiresAt)}`
                      : t.state === 'REVOKED'
                        ? 'Revoked'
                        : 'Expired'}
                    {' · '}
                    {t.lastUsedAt ? `last used ${formatDateTime(t.lastUsedAt)}` : 'never used'}
                  </p>
                </div>
                {t.state === 'ACTIVE' && (
                  <Button size="sm" variant="secondary" onClick={() => setRevoking(t)}>Revoke</Button>
                )}
              </li>
            ))}
          </ul>
        )}
      </div>
      <ConfirmDialog
        open={!!revoking}
        onClose={() => setRevoking(null)}
        onConfirm={revoke}
        loading={busy}
        danger
        title="Revoke this token?"
        confirmLabel="Revoke"
        message="The extension using it will stop working until you give it a new token."
      />
    </Card>
  );
}
