import { forwardRef, useId, useState } from 'react';
import { Eye, EyeOff } from 'lucide-react';

export function FieldShell({ label, error, hint, id, required, children }) {
  return (
    <div>
      {label && <label htmlFor={id} className="label">{label}{required && <span className="text-danger" aria-hidden="true"> *</span>}</label>}
      {children}
      {hint && !error && <p className="mt-1 text-xs text-fg-subtle">{hint}</p>}
      {error && <p id={`${id}-err`} role="alert" className="mt-1 text-xs text-danger">{error}</p>}
    </div>
  );
}

export const Input = forwardRef(function Input({ label, error, hint, required, className = '', ...rest }, ref) {
  const id = useId();
  return (
    <FieldShell {...{ label, error, hint, id, required }}>
      <input id={id} ref={ref} aria-invalid={!!error} aria-describedby={error ? `${id}-err` : undefined} className={`field ${className}`} {...rest} />
    </FieldShell>
  );
});

export const PasswordInput = forwardRef(function PasswordInput({ label, error, hint, required, ...rest }, ref) {
  const id = useId(); const [show, setShow] = useState(false);
  return (
    <FieldShell {...{ label, error, hint, id, required }}>
      <div className="relative">
        <input id={id} ref={ref} type={show ? 'text' : 'password'} aria-invalid={!!error} aria-describedby={error ? `${id}-err` : undefined} className="field pr-10" {...rest} />
        <button type="button" onClick={() => setShow((s) => !s)} aria-label={show ? 'Hide password' : 'Show password'} aria-pressed={show}
          className="absolute inset-y-0 right-0 flex w-10 items-center justify-center text-fg-subtle hover:text-fg">
          {show ? <EyeOff size={16} /> : <Eye size={16} />}
        </button>
      </div>
    </FieldShell>
  );
});

export const Textarea = forwardRef(function Textarea({ label, error, hint, required, rows = 4, ...rest }, ref) {
  const id = useId();
  return (
    <FieldShell {...{ label, error, hint, id, required }}>
      <textarea id={id} ref={ref} rows={rows} aria-invalid={!!error} className="field resize-y" {...rest} />
    </FieldShell>
  );
});

export const Select = forwardRef(function Select({ label, error, hint, required, options = [], placeholder, ...rest }, ref) {
  const id = useId();
  return (
    <FieldShell {...{ label, error, hint, id, required }}>
      <select id={id} ref={ref} aria-invalid={!!error} className="field" {...rest}>
        {placeholder !== undefined && <option value="">{placeholder}</option>}
        {options.map((o) => (typeof o === 'string' ? <option key={o} value={o}>{o}</option> : <option key={o.value} value={o.value}>{o.label}</option>))}
      </select>
    </FieldShell>
  );
});

export const Checkbox = forwardRef(function Checkbox({ label, error, ...rest }, ref) {
  const id = useId();
  return (
    <div>
      <label htmlFor={id} className="flex cursor-pointer items-start gap-2 text-sm text-fg-muted">
        <input id={id} ref={ref} type="checkbox" className="mt-0.5 h-4 w-4 rounded border-border-strong accent-[rgb(var(--color-primary))]" {...rest} />
        <span>{label}</span>
      </label>
      {error && <p role="alert" className="mt-1 text-xs text-danger">{error}</p>}
    </div>
  );
});
