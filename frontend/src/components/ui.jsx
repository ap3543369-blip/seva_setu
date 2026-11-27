import { useEffect } from 'react';
import { STATUS_TONE, initials } from '../utils.js';

export const Spinner = ({ label = 'Loading…' }) => (
  <div className="center-note"><span className="spinner" aria-hidden="true" /> {label}</div>
);

export const Empty = ({ icon = '🍽️', title, children }) => (
  <div className="empty">
    <div className="empty-icon">{icon}</div>
    <strong>{title}</strong>
    {children && <p>{children}</p>}
  </div>
);

export const ErrorBox = ({ error, onRetry }) => (
  <div className="alert bad" role="alert">
    {error?.message || 'Something went wrong.'}
    {onRetry && <button className="btn btn-sm btn-ghost" onClick={onRetry}>Try again</button>}
  </div>
);

/** Shows a spinner / error, or the children once `data` is available. */
export function Async({ state, children }) {
  if (state.loading && !state.data) return <Spinner />;
  if (state.error) return <ErrorBox error={state.error} onRetry={state.reload} />;
  return children(state.data);
}

export const StatusBadge = ({ status }) => <span className={`badge ${STATUS_TONE[status] || 'info'}`}>{status}</span>;

export const Avatar = ({ name, size = 40 }) => (
  <span className="avatar" style={{ width: size, height: size, fontSize: size * 0.38 }} aria-hidden="true">{initials(name)}</span>
);

export const Stat = ({ icon, label, value, tone = 'green', hint }) => (
  <div className={`stat tone-${tone}`}>
    <span className="stat-icon" aria-hidden="true">{icon}</span>
    <div>
      <div className="stat-value">{value}</div>
      <div className="stat-label">{label}</div>
      {hint && <div className="stat-hint">{hint}</div>}
    </div>
  </div>
);

export const PageHead = ({ title, subtitle, children }) => (
  <div className="page-head">
    <div>
      <h1>{title}</h1>
      {subtitle && <p className="muted">{subtitle}</p>}
    </div>
    {children && <div className="page-head-actions">{children}</div>}
  </div>
);

export const Progress = ({ value, max = 100 }) => (
  <div className="progress" role="progressbar" aria-valuenow={Math.round(value)} aria-valuemin="0" aria-valuemax={max}>
    <span style={{ width: `${Math.min(100, (value / max) * 100)}%` }} />
  </div>
);

export function Field({ label, error, hint, children, htmlFor }) {
  return (
    <label className={`field ${error ? 'has-error' : ''}`} htmlFor={htmlFor}>
      <span className="field-label">{label}</span>
      {children}
      {hint && !error && <small className="muted">{hint}</small>}
      {error && <small className="field-error">{error}</small>}
    </label>
  );
}

export function Modal({ title, onClose, children, footer, wide }) {
  useEffect(() => {
    const onKey = e => e.key === 'Escape' && onClose();
    document.addEventListener('keydown', onKey);
    document.body.style.overflow = 'hidden';
    return () => { document.removeEventListener('keydown', onKey); document.body.style.overflow = ''; };
  }, [onClose]);
  return (
    <div className="modal-backdrop" onMouseDown={e => e.target === e.currentTarget && onClose()}>
      <div className={`modal ${wide ? 'wide' : ''}`} role="dialog" aria-modal="true" aria-label={title}>
        <div className="modal-head">
          <h3>{title}</h3>
          <button className="icon-btn" onClick={onClose} aria-label="Close">✕</button>
        </div>
        <div className="modal-body">{children}</div>
        {footer && <div className="modal-foot">{footer}</div>}
      </div>
    </div>
  );
}

export function Tabs({ tabs, value, onChange }) {
  return (
    <div className="tabs" role="tablist">
      {tabs.map(t => (
        <button key={t.key} role="tab" aria-selected={value === t.key} className={`tab ${value === t.key ? 'active' : ''}`} onClick={() => onChange(t.key)}>
          {t.label}{t.count != null && <em>{t.count}</em>}
        </button>
      ))}
    </div>
  );
}
