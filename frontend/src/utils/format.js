const dateFmt = new Intl.DateTimeFormat(undefined, { day: 'numeric', month: 'short', year: 'numeric' });
const dateTimeFmt = new Intl.DateTimeFormat(undefined, { day: 'numeric', month: 'short', year: 'numeric', hour: 'numeric', minute: '2-digit' });

export const formatDate = (v) => (v ? dateFmt.format(new Date(v)) : '—');
export const formatDateTime = (v) => (v ? dateTimeFmt.format(new Date(v)) : '—');
export const pct = (n) => `${Math.round(n * 10) / 10}%`;

/** Days from now to a date (negative = past). */
export function daysUntil(v) {
  if (!v) return null;
  const ms = new Date(v).getTime() - Date.now();
  return Math.ceil(ms / 86400000);
}
export const isOverdue = (v) => v && new Date(v).getTime() < Date.now();

/** Ratio helper that never divides by zero. Returns null when the denominator is 0. */
export const ratio = (num, den) => (den > 0 ? (num / den) * 100 : null);

export function startOfWeek(d = new Date()) {
  const x = new Date(d); const day = (x.getDay() + 6) % 7; // Monday start
  x.setHours(0, 0, 0, 0); x.setDate(x.getDate() - day); return x;
}
