import { useEffect, useState, useCallback } from 'react';

export const fmt = n => Number(n || 0).toLocaleString('en-IN');
const MONTHS = ['Jan', 'Feb', 'Mar', 'Apr', 'May', 'Jun', 'Jul', 'Aug', 'Sep', 'Oct', 'Nov', 'Dec'];
const time12 = d => `${d.getHours() % 12 || 12}:${String(d.getMinutes()).padStart(2, '0')} ${d.getHours() < 12 ? 'AM' : 'PM'}`;
/** "2026-10-13" (a LocalDate) must not be read as UTC midnight, or some time zones would show the previous day. */
const parse = iso => { const m = /^(\d{4})-(\d{2})-(\d{2})$/.exec(iso); return m ? new Date(+m[1], +m[2] - 1, +m[3]) : new Date(iso); };
export const fmtDate = iso => { if (!iso) return ''; const d = parse(iso); return `${String(d.getDate()).padStart(2, '0')} ${MONTHS[d.getMonth()]} ${d.getFullYear()}`; };
export const fmtDateTime = iso => (iso ? `${fmtDate(iso)}, ${time12(new Date(iso))}` : '');
export const fmtPickup = iso => { if (!iso) return ''; const d = parse(iso); return `${d.getDate()} ${MONTHS[d.getMonth()]}, ${time12(d)}`; };
export const monthLabel = ym => { const [y, m] = ym.split('-'); return `${MONTHS[+m - 1]} ${y}`; };

export const initials = name => String(name || '?').trim().split(/\s+/).slice(0, 2).map(w => w[0]).join('').toUpperCase() || '?';
export const foodImage = d => (d.images && d.images.length ? d.images[0] : `/assets/images/food/${d.imageKey || 'veg-biryani'}.svg`);

export const STATUS_TONE = { Pending: 'warn', 'Awaiting approval': 'warn', Accepted: 'info', Delivered: 'ok', Cancelled: 'bad', Declined: 'bad', Rejected: 'bad' };

/** Load data once (and on demand): const { data, loading, error, reload, setData } = useFetch(() => api('/x'), [deps]) */
export function useFetch(fn, deps = []) {
  const [state, setState] = useState({ data: null, loading: true, error: null });
  const run = useCallback(() => {
    setState(s => ({ ...s, loading: true, error: null }));
    return fn().then(
      data => setState({ data, loading: false, error: null }),
      error => setState({ data: null, loading: false, error })
    );
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, deps);
  useEffect(() => { run(); }, [run]);
  return { ...state, reload: run, setData: data => setState(s => ({ ...s, data })) };
}

export function downloadCsv(filename, rows) {
  const esc = v => `"${String(v ?? '').replace(/"/g, '""')}"`;
  const csv = rows.map(r => r.map(esc).join(',')).join('\n');
  const a = document.createElement('a');
  a.href = URL.createObjectURL(new Blob([csv], { type: 'text/csv;charset=utf-8;' }));
  a.download = filename;
  a.click();
  URL.revokeObjectURL(a.href);
}
