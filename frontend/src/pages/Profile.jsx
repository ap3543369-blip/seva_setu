import { useState } from 'react';
import { api, put } from '../api.js';
import { useAuth } from '../auth.jsx';
import { useToast } from '../toast.jsx';
import { fmt, useFetch } from '../utils.js';
import { Avatar, Field, PageHead, Stat } from '../components/ui.jsx';

const TYPES = { donor: ['Restaurant / Canteen', 'Mess', 'Hotel / Caterer', 'Household / Event'], ngo: ['NGO', 'Charity trust', 'Community kitchen', 'Food bank'], recipient: ['Individual', 'Student', 'Institution'], hospital: ['Hospital', 'Pharmacy', 'Clinic'], recycler: ['Recycler', 'E-waste collector'] };

export default function Profile() {
  const { user, setUser } = useAuth();
  const toast = useToast();
  const stats = useFetch(() => (user.role === 'ngo' ? api('/dashboard/ngo') : user.role === 'donor' ? api('/dashboard/donor') : Promise.resolve(null)), [user.role]);
  const [f, setF] = useState({ name: user.name, orgType: user.orgType || TYPES[user.role]?.[0] || '', email: user.email, phone: user.phone || '' });
  const [errors, setErrors] = useState({});
  const [busy, setBusy] = useState(false);
  const set = k => e => { setF(x => ({ ...x, [k]: e.target.value })); setErrors(x => ({ ...x, [k]: undefined })); };
  const options = [...new Set([...(TYPES[user.role] || []), f.orgType].filter(Boolean))];

  const save = async e => {
    e.preventDefault();
    setBusy(true);
    try { const u = await put('/profile', f); setUser(u); setErrors({}); toast('Profile updated.'); }
    catch (ex) { setErrors(ex.errors || {}); toast(ex.message, 'error'); }
    finally { setBusy(false); }
  };

  const s = stats.data;
  return (
    <>
      <PageHead title="My profile" subtitle="Manage your information and see your contribution." />
      <div className="profile-grid">
        <div className="card profile-card">
          <Avatar name={user.name} size={84} />
          <h2>{user.name}</h2>
          <span className={`role-pill ${user.role}`}>{user.role.toUpperCase()}</span>
          {['ngo', 'hospital', 'recycler'].includes(user.role) && <span className={`badge ${user.verified ? 'ok' : 'warn'}`}>{user.verified ? '✔ Verified' : 'Verification pending'}</span>}
          <p className="muted">{user.email}</p>
          {user.role === 'donor' && <div className="points-chip">⭐ {fmt(user.points)} points</div>}
          {s && (
            <div className="mini-stats">
              {user.role === 'ngo'
                ? <><div><b>{fmt(s.requests)}</b><small>Requests</small></div><div><b>{fmt(s.fulfilled)}</b><small>Delivered</small></div><div><b>{fmt(s.meals)}</b><small>Meals</small></div></>
                : <><div><b>{fmt(s.donations)}</b><small>Donations</small></div><div><b>{fmt(s.meals)}</b><small>Meals</small></div><div><b>{fmt(s.co2)}</b><small>kg CO₂</small></div></>}
            </div>
          )}
        </div>
        <form className="card" onSubmit={save} noValidate>
          <h3>Edit details</h3>
          <div className="form-grid">
            <Field label="Name" error={errors.name}><input value={f.name} onChange={set('name')} /></Field>
            <Field label="Organisation type" error={errors.orgType}><select value={f.orgType} onChange={set('orgType')}>{options.map(o => <option key={o}>{o}</option>)}</select></Field>
            <Field label="Email" error={errors.email}><input type="email" value={f.email} onChange={set('email')} /></Field>
            <Field label="Mobile number" error={errors.phone} hint="10-digit Indian number, optional +91"><input value={f.phone} onChange={set('phone')} /></Field>
          </div>
          <button className="btn btn-primary" disabled={busy}>{busy ? 'Saving…' : 'Save changes'}</button>
        </form>
      </div>
    </>
  );
}
