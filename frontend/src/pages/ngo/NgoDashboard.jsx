import { useMemo, useState } from 'react';
import { Link } from 'react-router-dom';
import { api, patch } from '../../api.js';
import { useAuth } from '../../auth.jsx';
import { useToast } from '../../toast.jsx';
import { fmt, fmtPickup, foodImage, monthLabel, useFetch } from '../../utils.js';
import { Async, Empty, PageHead, Stat, StatusBadge } from '../../components/ui.jsx';
import { BarChart } from '../../components/charts.jsx';

export default function NgoDashboard() {
  const { user } = useAuth();
  const toast = useToast();
  const months = useMemo(() => Array.from({ length: 6 }, (_, i) => { const d = new Date(); d.setDate(1); d.setMonth(d.getMonth() - i); return `${d.getFullYear()}-${String(d.getMonth() + 1).padStart(2, '0')}`; }), []);
  const [month, setMonth] = useState(months[0]);
  const dash = useFetch(() => api('/dashboard/ngo'));
  const chart = useFetch(() => api(`/dashboard/ngo/chart?month=${month}`), [month]);
  const reqs = useFetch(() => api('/requests/mine'));

  const act = async (r, status) => {
    try { await patch(`/requests/${r.id}/status`, { status }); toast(status === 'Accepted' ? 'Request accepted.' : 'Marked as delivered — thank you!'); reqs.reload(); dash.reload(); chart.reload(); }
    catch (e) { toast(e.message, 'error'); }
  };

  return (
    <>
      <PageHead title={`Welcome, ${user.name}!`} subtitle="Manage food requests and track your impact.">
        <Link to="/ngo/browse" className="btn btn-primary">🔎 Find food</Link>
      </PageHead>
      {!user.verified && <div className="alert warn">⏳ Your NGO is waiting for admin verification. You can browse food, but requests unlock once you are verified.</div>}
      <Async state={dash}>{d => (
        <div className="stat-grid four">
          <Stat icon="📥" label="Requests made" value={fmt(d.requests)} />
          <Stat icon="✅" label="Fulfilled" value={fmt(d.fulfilled)} tone="orange" />
          <Stat icon="🍽️" label="Meals received" value={fmt(d.meals)} tone="blue" />
          <Stat icon="🤝" label="Partner donors" value={fmt(d.partners)} tone="purple" />
        </div>
      )}</Async>
      <div className="two-col wide-left">
        <div className="card">
          <div className="row-between"><h3>Recent requests</h3><Link to="/ngo/requests" className="link">Open board</Link></div>
          <Async state={reqs}>{rows => rows.length === 0 ? <Empty icon="📋" title="No requests yet">Find food nearby and send your first request.</Empty> : (
            <ul className="rows">
              {rows.slice(0, 5).map(r => (
                <li key={r.id}>
                  <img src={foodImage(r)} alt="" />
                  <div><Link to={`/donation/${r.donationId}`}><strong>{r.food}</strong></Link> <small>({r.qty})</small><br /><small className="muted">{r.donor} · {r.distanceKm} km · {fmtPickup(r.pickupAt)}</small></div>
                  <div className="row-gap">
                    <StatusBadge status={r.status} />
                    {r.status === 'Pending' && <button className="btn btn-sm btn-ghost" onClick={() => act(r, 'Accepted')}>Accept</button>}
                    {r.status === 'Accepted' && <button className="btn btn-sm btn-primary" onClick={() => act(r, 'Delivered')}>Mark delivered</button>}
                  </div>
                </li>
              ))}
            </ul>
          )}</Async>
        </div>
        <div className="card">
          <div className="row-between"><h3>Food received</h3><select className="select-sm" value={month} onChange={e => setMonth(e.target.value)} aria-label="Month">{months.map(m => <option key={m} value={m}>{monthLabel(m)}</option>)}</select></div>
          <Async state={chart}>{c => <BarChart values={c.values} labels={c.values.map((_, i) => `W${i + 1}`)} unit=" meals" height={210} />}</Async>
        </div>
      </div>
    </>
  );
}
