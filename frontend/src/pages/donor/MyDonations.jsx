import { useMemo, useState } from 'react';
import { Link } from 'react-router-dom';
import { api, del } from '../../api.js';
import { useAuth } from '../../auth.jsx';
import { useToast } from '../../toast.jsx';
import { downloadCsv, fmt, fmtDate, fmtDateTime, useFetch } from '../../utils.js';
import { Async, Empty, Modal, PageHead, StatusBadge, Tabs } from '../../components/ui.jsx';

export default function MyDonations() {
  const { user } = useAuth();
  const toast = useToast();
  const state = useFetch(() => api('/donations/mine'));
  const [tab, setTab] = useState('All');
  const [q, setQ] = useState('');
  const [cert, setCert] = useState(null);

  const list = state.data || [];
  const counts = useMemo(() => list.reduce((m, d) => ({ ...m, [d.status]: (m[d.status] || 0) + 1 }), {}), [list]);
  const shown = list.filter(d => (tab === 'All' || d.status === tab) && `${d.food} ${d.ngo || ''} ${d.area}`.toLowerCase().includes(q.toLowerCase()));

  const cancel = async d => {
    if (!window.confirm(`Cancel "${d.food}"? Points earned for it will be taken back.`)) return;
    try { await del(`/donations/${d.id}`); toast('Donation cancelled.'); state.reload(); } catch (e) { toast(e.message, 'error'); }
  };
  const exportCsv = () => downloadCsv('my-donations.csv', [['Date', 'Food', 'Quantity', 'Meals', 'NGO', 'Status', 'Points'], ...list.map(d => [fmtDate(d.createdAt), d.food, d.qty, d.meals, d.ngo || '', d.status, d.points])]);

  return (
    <>
      <PageHead title="My donations" subtitle="Track every donation and download certificates for delivered ones.">
        <button className="btn btn-ghost" onClick={exportCsv} disabled={!list.length}>⬇ Export CSV</button>
        <Link to="/donor/donate" className="btn btn-primary">➕ Donate food</Link>
      </PageHead>
      <Tabs value={tab} onChange={setTab} tabs={['All', 'Awaiting approval', 'Pending', 'Accepted', 'Delivered', 'Cancelled', 'Rejected'].map(k => ({ key: k, label: k, count: k === 'All' ? list.length : counts[k] || 0 }))} />
      <input className="search" placeholder="🔎  Search food, NGO or area…" value={q} onChange={e => setQ(e.target.value)} aria-label="Search donations" />
      <Async state={state}>{() => shown.length === 0 ? <Empty icon="📦" title="Nothing here yet">Donations that match your filter will appear here.</Empty> : (
        <div className="card table-wrap">
          <table className="table">
            <thead><tr><th>Date</th><th>Food</th><th>Quantity</th><th>NGO</th><th>Status</th><th>Points</th><th /></tr></thead>
            <tbody>
              {shown.map(d => (
                <tr key={d.id}>
                  <td data-label="Date">{fmtDate(d.createdAt)}</td>
                  <td data-label="Food"><Link to={`/donation/${d.id}`}><strong>{d.food}</strong></Link></td>
                  <td data-label="Quantity">{d.qty}</td>
                  <td data-label="NGO">{d.ngo || '—'}</td>
                  <td data-label="Status"><StatusBadge status={d.status} />{d.status === 'Rejected' && d.reviewNote && <><br /><small className="muted">{d.reviewNote}</small></>}</td>
                  <td data-label="Points">{['Cancelled', 'Rejected'].includes(d.status) ? '—' : d.status === 'Awaiting approval' ? 'on approval' : `+${d.points}`}</td>
                  <td className="actions">
                    {(d.status === 'Pending' || d.status === 'Awaiting approval') && <button className="btn btn-sm btn-danger" onClick={() => cancel(d)}>Cancel</button>}
                    {d.status === 'Delivered' && <button className="btn btn-sm btn-ghost" onClick={() => setCert(d)}>🏅 Certificate</button>}
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      )}</Async>
      {cert && (
        <Modal title="Donation certificate" wide onClose={() => setCert(null)} footer={<><button className="btn btn-ghost" onClick={() => setCert(null)}>Close</button><button className="btn btn-primary" onClick={() => window.print()}>🖨 Print / Save as PDF</button></>}>
          <div className="certificate">
            <img src="/assets/icons/logo.svg" alt="" width="56" />
            <small>CERTIFICATE OF APPRECIATION</small>
            <h2>{user.name}</h2>
            <p>generously donated <strong>{cert.qty}</strong> of <strong>{cert.food}</strong>, providing about <strong>{fmt(cert.meals)} meals</strong> to people in need through <strong>{cert.ngo}</strong>.</p>
            <p className="muted">Delivered on record dated {fmtDateTime(cert.createdAt)}</p>
            <div className="seal">🌱<br />FoodShare</div>
          </div>
        </Modal>
      )}
    </>
  );
}
