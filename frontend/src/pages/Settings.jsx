import { useState } from 'react';
import { post } from '../api.js';
import { useAuth } from '../auth.jsx';
import { useToast } from '../toast.jsx';
import { Field, PageHead } from '../components/ui.jsx';

export default function Settings() {
  const { user } = useAuth();
  const toast = useToast();
  const [f, setF] = useState({ currentPassword: '', newPassword: '', confirm: '' });
  const [errors, setErrors] = useState({});
  const [busy, setBusy] = useState(false);
  const [dark, setDark] = useState(document.documentElement.dataset.theme === 'dark');
  const set = k => e => { setF(x => ({ ...x, [k]: e.target.value })); setErrors(x => ({ ...x, [k]: undefined })); };

  const toggleDark = () => {
    const next = !dark; setDark(next);
    document.documentElement.dataset.theme = next ? 'dark' : 'light';
    localStorage.setItem('fs_theme', next ? 'dark' : 'light');
  };

  const submit = async e => {
    e.preventDefault();
    const err = {};
    if (!f.currentPassword) err.currentPassword = 'Enter your current password.';
    if (f.newPassword.length < 6) err.newPassword = 'New password must be at least 6 characters.';
    if (f.newPassword !== f.confirm) err.confirm = 'Passwords do not match.';
    setErrors(err);
    if (Object.keys(err).length) return;
    setBusy(true);
    try {
      await post('/profile/password', { currentPassword: f.currentPassword, newPassword: f.newPassword });
      toast('Password changed.'); setF({ currentPassword: '', newPassword: '', confirm: '' });
    } catch (ex) { setErrors({ ...(ex.errors || {}), currentPassword: ex.errors?.currentPassword || (ex.status === 400 || ex.status === 401 ? ex.message : undefined) }); toast(ex.message, 'error'); }
    finally { setBusy(false); }
  };

  return (
    <>
      <PageHead title="Settings" subtitle={`Signed in as ${user.email}`} />
      <div className="two-col">
        <form className="card" onSubmit={submit} noValidate>
          <h3>Change password</h3>
          <Field label="Current password" error={errors.currentPassword}><input type="password" autoComplete="current-password" value={f.currentPassword} onChange={set('currentPassword')} /></Field>
          <Field label="New password" error={errors.newPassword}><input type="password" autoComplete="new-password" value={f.newPassword} onChange={set('newPassword')} /></Field>
          <Field label="Confirm new password" error={errors.confirm}><input type="password" autoComplete="new-password" value={f.confirm} onChange={set('confirm')} /></Field>
          <button className="btn btn-primary" disabled={busy}>{busy ? 'Updating…' : 'Update password'}</button>
        </form>
        <div className="card">
          <h3>Appearance</h3>
          <div className="row-between setting">
            <div><strong>Dark mode</strong><p className="muted">Easier on the eyes at night.</p></div>
            <button className={`switch ${dark ? 'on' : ''}`} role="switch" aria-checked={dark} onClick={toggleDark}><i /></button>
          </div>
        </div>
      </div>
    </>
  );
}
