import { useState } from 'react';
import { Link } from 'react-router-dom';
import { api, post } from '../../api.js';
import { useAuth, VERIFY_ROLES } from '../../auth.jsx';
import { useToast } from '../../toast.jsx';
import { downloadCsv, fmtDate, useFetch } from '../../utils.js';
import { Async, Avatar, Empty, Modal, PageHead, Tabs } from '../../components/ui.jsx';

const MOD_STATUS = { available: 'Access', coming_soon: 'Module off', not_for_role: 'Not for role', revoked: 'Revoked', needs_verification: 'Needs verification' };

function UserModulesModal({ user, onClose }) {
  const toast = useToast();
  const state = useFetch(() => api(`/admin/users/${user.id}/modules`), [user.id]);
  const set = async (key, allowed) => {
    try { state.setData(await post(`/admin/users/${user.id}/modules/${key}`, { allowed })); }
    catch (e) { toast(e.message, 'error'); }
  };
  return (
    <Modal title={`Modules · ${user.name}`} onClose={onClose} footer={<button className="btn btn-primary" onClick={onClose}>Done</button>}>
      <Async state={state}>{rows => (
        <ul className="rows">
          {rows.map(m => (
            <li key={m.key}>
              <div><strong>{m.label}</strong><br /><small className="muted">{MOD_STATUS[m.status] || m.status}{m.override ? ` · ${m.override} by admin` : ''}</small></div>
              <div className="row-gap">
                <button className="btn btn-sm btn-ghost" onClick={() => set(m.key, true)}>Grant</button>
                <button className="btn btn-sm btn-danger" onClick={() => set(m.key, false)}>Revoke</button>
                {m.override && <button className="btn btn-sm btn-ghost" onClick={() => set(m.key, null)}>Reset</button>}
              </div>
            </li>
          ))}
        </ul>
      )}</Async>
    </Modal>
  );
}

export default function AdminUsers({ only }) {
  const { user: me } = useAuth();
  const toast = useToast();
  const state = useFetch(() => api('/admin/users'));
  const [q, setQ] = useState('');
  const [tab, setTab] = useState(only === 'ngo' ? 'pending' : 'all');
  const [modUser, setModUser] = useState(null);

  const act = async (u, what) => {
    try { await post(`/admin/users/${u.id}/${what}`); toast(what === 'verify' ? `${u.name} verified.` : u.blocked ? `${u.name} unblocked.` : `${u.name} blocked.`); state.reload(); }
    catch (e) { toast(e.message, 'error'); }
  };

  const all = (state.data || []).filter(u => (only === 'ngo' ? VERIFY_ROLES.includes(u.role) : true));
  const tabs = only === 'ngo'
    ? [{ key: 'pending', label: 'Pending', count: all.filter(u => !u.verified).length }, { key: 'verified', label: 'Verified', count: all.filter(u => u.verified).length }, { key: 'all', label: 'All', count: all.length }]
    : [{ key: 'all', label: 'All', count: all.length }, { key: 'donor', label: 'Donors', count: all.filter(u => u.role === 'donor').length }, { key: 'ngo', label: 'NGOs', count: all.filter(u => u.role === 'ngo').length }, { key: 'other', label: 'Other roles', count: all.filter(u => !['donor', 'ngo'].includes(u.role)).length }, { key: 'blocked', label: 'Blocked', count: all.filter(u => u.blocked).length }];
  const shown = all.filter(u => {
    if (tab === 'pending') return !u.verified;
    if (tab === 'verified') return u.verified;
    if (tab === 'blocked') return u.blocked;
    if (tab === 'donor' || tab === 'ngo') return u.role === tab;
    if (tab === 'other') return !['donor', 'ngo'].includes(u.role);
    return true;
  }).filter(u => `${u.name} ${u.email}`.toLowerCase().includes(q.toLowerCase()));

  return (
    <>
      <PageHead title={only === 'ngo' ? 'Verification' : 'Users'} subtitle={only === 'ngo' ? 'Verify NGOs, hospitals and recyclers before all features unlock.' : 'Search, verify and block accounts.'}>
        <button className="btn btn-ghost" disabled={!all.length} onClick={() => downloadCsv('users.csv', [['Name', 'Email', 'Role', 'Type', 'Verified', 'Blocked', 'Joined'], ...all.map(u => [u.name, u.email, u.role, u.orgType, u.verified, u.blocked, fmtDate(u.createdAt)])])}>⬇ Export CSV</button>
      </PageHead>
      <Tabs value={tab} onChange={setTab} tabs={tabs} />
      <input className="search" placeholder="🔎  Search name or email…" value={q} onChange={e => setQ(e.target.value)} aria-label="Search users" />
      <Async state={state}>{() => shown.length === 0 ? <Empty icon="👥" title="No users here">Nothing matches this filter.</Empty> : (
        <div className="card table-wrap">
          <table className="table">
            <thead><tr><th>User</th><th>Role</th><th>Status</th><th>Joined</th><th /></tr></thead>
            <tbody>
              {shown.map(u => (
                <tr key={u.id}>
                  <td data-label="User"><div className="cell-user"><Avatar name={u.name} size={34} /><div><strong>{u.name}</strong><br /><small className="muted">{u.email}</small></div></div></td>
                  <td data-label="Role"><span className={`role-pill ${u.role}`}>{u.role}</span><br /><small className="muted">{u.orgType}</small></td>
                  <td data-label="Status">{u.blocked ? <span className="badge bad">Blocked</span> : u.verified ? <span className="badge ok">{VERIFY_ROLES.includes(u.role) ? 'Verified' : 'Active'}</span> : <span className="badge warn">Pending</span>}</td>
                  <td data-label="Joined">{fmtDate(u.createdAt)}</td>
                  <td className="actions">
                    {VERIFY_ROLES.includes(u.role) && !u.verified && (u.role === 'ngo' ? <Link className="btn btn-sm btn-primary" to="/admin/approvals?tab=ngo">Review application</Link> : <button className="btn btn-sm btn-primary" onClick={() => act(u, 'verify')}>Verify</button>)}
                    {u.role !== 'admin' && <button className="btn btn-sm btn-ghost" onClick={() => setModUser(u)}>Modules</button>}
                    {u.role !== 'admin' && u.id !== me.id && <button className={`btn btn-sm ${u.blocked ? 'btn-ghost' : 'btn-danger'}`} onClick={() => act(u, 'block')}>{u.blocked ? 'Unblock' : 'Block'}</button>}
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      )}</Async>
      {modUser && <UserModulesModal user={modUser} onClose={() => setModUser(null)} />}
    </>
  );
}
