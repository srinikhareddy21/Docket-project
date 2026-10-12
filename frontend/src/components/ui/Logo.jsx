/** DOCKET mark: a docket sheet with a checkmark. Uses currentColor so it follows tokens. */
export function LogoMark({ size = 28, className = '' }) {
  return (
    <svg width={size} height={size} viewBox="0 0 32 32" fill="none" className={className} aria-hidden="true">
      <rect x="5" y="3" width="22" height="26" rx="4" fill="currentColor" />
      <path d="M10 11h12M10 16h7" stroke="rgb(var(--color-surface))" strokeWidth="2" strokeLinecap="round" />
      <path d="M15 22.5l3 3 6-7" stroke="rgb(var(--color-surface))" strokeWidth="2.4" strokeLinecap="round" strokeLinejoin="round" />
    </svg>
  );
}
export function Logo({ size = 28, withText = true, className = '' }) {
  return (
    <span className={`inline-flex items-center gap-2 text-primary ${className}`}>
      <LogoMark size={size} />
      {withText && <span className="font-display text-xl font-bold tracking-tight text-fg">DOCKET</span>}
    </span>
  );
}
