import { useMemo } from 'react';
import { Link } from 'react-router-dom';
import { api, post } from '../../api.js';
import { useToast } from '../../toast.jsx';
import { VERIFY_ROLES } from '../../auth.jsx';
import { fmt, useFetch } from '../../utils.js';
import { Async, Avatar, PageHead, Stat } from '../../components/ui.jsx';
import { BarChart, Donut } from '../../components/charts.jsx';

const MON = ['Jan', 'Feb', 'Mar', 'Apr', 'May', 'Jun', 'Jul', 'Aug', 'Sep', 'Oct', 'Nov', 'Dec'];
const COLORS = { Pending: '#F5A524', Accepted: '#2E7BE6', Delivered: '#168A4A', Cancelled: '#E5484D' };

export default function AdminOverview() {
  const toast = useToast();
  const stats = useFetch(() => api('/admin/stats'));
  const donations = useFetch(() => api('/admin/donations'));
  const users = useFetch(() => api('/admin/users'));
  const approvals = useFetch(() => api('/admin/approvals/summary'));

  const derived = useMemo(() => {
    const list = donations.data || [];
    const byStatus = ['Pending', 'Accepted', 'Delivered', 'Cancelled'].map(s => ({ label: s, value: list.filter(d => d.status === s).length, color: COLORS[s] }));
    const now = new Date();
    const months = Array.from({ length: 6 }, (_, i) => { const d = new Date(now.getFullYear(), now.getMonth() - 5 + i, 1); return { key: `${d.getFullYear()}-${d.getMonth()}`, label: MON[d.getMonth()], v: 0 }; });
    list.filter(d => d.status !== 'Cancelled').forEach(d => { const c = new Date(d.createdAt); const m = months.find(x => x.key === `${c.getFullYear()}-${c.getMonth()}`); if (m) m.v += d.meals; });
    const donors = {};
    list.filter(d => d.status === 'Delivered').forEach(d => { donors[d.donor] = (donors[d.donor] || 0) + d.meals; });
    const top = Object.entries(donors).sort((a, b) => b[1] - a[1]).slice(0, 5);
    return { byStatus, months, top };
  }, [donations.data]);

  const pending = (users.data || []).filter(u => VERIFY_ROLES.includes(u.role) && !u.verified && !u.blocked);
  const verify = async u => {
    try { await post(`/admin/users/${u.id}/verify`); toast(`${u.name} verified.`); users.reload(); stats.reload(); } catch (e) { toast(e.message, 'error'); }
  };

  return (
    <>
      <PageHead title="Admin overview" subtitle="Platform health at a glance." />
      <Async state={approvals}>{a => a.total > 0 ? (
        <div className="alert warn attention"><span>🛎️ <strong>{a.total}</strong> item(s) are waiting for your decision: {a.ngoApplications} NGO application(s), {a.food} food, {a.books} book listing(s), {a.medicine} medicine listing(s).</span><Link className="btn btn-sm btn-primary" to="/admin/approvals">Review now</Link></div>
      ) : null}</Async>
      <Async state={stats}>{s => (
        <div className="stat-grid">
          <Stat icon="🍲" label="Donors" value={fmt(s.donors)} />
          <Stat icon="🤝" label="NGOs" value={fmt(s.ngos)} tone="blue" hint={s.pendingNgos ? `${s.pendingNgos} awaiting verification` : 'all verified'} />
          <Stat icon="📦" label="Donations" value={fmt(s.donations)} tone="orange" />
          <Stat icon="✅" label="Delivered" value={fmt(s.delivered)} tone="purple" />
          <Stat icon="🍽️" label="Meals" value={fmt(s.meals)} tone="pink" />
        </div>
      )}</Async>
      <div className="two-col">
        <div className="card"><h3>Donations by status</h3><Async state={donations}>{() => <Donut segments={derived.byStatus} />}</Async></div>
        <div className="card"><h3>Meals donated · last 6 months</h3><Async state={donations}>{() => <BarChart values={derived.months.map(m => m.v)} labels={derived.months.map(m => m.label)} height={190} />}</Async></div>
      </div>
      <div className="two-col">
        <div className="card">
          <div className="row-between"><h3>Accounts waiting for verification</h3><Link to="/admin/ngos" className="link">Manage</Link></div>
          <Async state={users}>{() => pending.length === 0 ? <p className="muted">🎉 No NGOs waiting right now.</p> : (
            <ul className="rows">{pending.slice(0, 5).map(u => <li key={u.id}><Avatar name={u.name} /><div><strong>{u.name}</strong> <span className={`role-pill ${u.role}`}>{u.role}</span><br /><small className="muted">{u.email}</small></div>{u.role === 'ngo' ? <Link className="btn btn-sm btn-primary" to="/admin/approvals?tab=ngo">Review</Link> : <button className="btn btn-sm btn-primary" onClick={() => verify(u)}>Verify</button>}</li>)}</ul>
          )}</Async>
        </div>
        <div className="card">
          <h3>Top donors (delivered meals)</h3>
          {derived.top.length === 0 ? <p className="muted">No delivered donations yet.</p> : (
            <ol className="leader">{derived.top.map(([n, m], i) => <li key={n}><span className="rank">{i + 1}</span><Avatar name={n} /><div><strong>{n}</strong></div><b>{fmt(m)} meals</b></li>)}</ol>
          )}
        </div>
      </div>
    </>
  );
}
