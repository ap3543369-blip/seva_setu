import { useState } from 'react';
import { Link } from 'react-router-dom';
import { api } from '../../api.js';
import { downloadCsv, fmtDate, useFetch } from '../../utils.js';
import { Async, Empty, PageHead, StatusBadge, Tabs } from '../../components/ui.jsx';

export default function AdminDonations() {
  const state = useFetch(() => api('/admin/donations'));
  const [tab, setTab] = useState('All');
  const [q, setQ] = useState('');
  const list = state.data || [];
  const shown = list.filter(d => (tab === 'All' || d.status === tab) && `${d.food} ${d.donor} ${d.ngo || ''} ${d.area}`.toLowerCase().includes(q.toLowerCase()));
  return (
    <>
      <PageHead title="All donations" subtitle="Every donation on the platform.">
        <button className="btn btn-ghost" disabled={!list.length} onClick={() => downloadCsv('donations.csv', [['ID', 'Date', 'Donor', 'Food', 'Quantity', 'Meals', 'NGO', 'Status', 'Area'], ...list.map(d => [d.id, fmtDate(d.createdAt), d.donor, d.food, d.qty, d.meals, d.ngo || '', d.status, d.area])])}>⬇ Export CSV</button>
      </PageHead>
      <Tabs value={tab} onChange={setTab} tabs={['All', 'Pending', 'Accepted', 'Delivered', 'Cancelled'].map(k => ({ key: k, label: k, count: k === 'All' ? list.length : list.filter(d => d.status === k).length }))} />
      <input className="search" placeholder="🔎  Search food, donor, NGO or area…" value={q} onChange={e => setQ(e.target.value)} aria-label="Search donations" />
      <Async state={state}>{() => shown.length === 0 ? <Empty icon="📦" title="No donations found" /> : (
        <div className="card table-wrap">
          <table className="table">
            <thead><tr><th>#</th><th>Date</th><th>Donor</th><th>Food</th><th>NGO</th><th>Meals</th><th>Status</th></tr></thead>
            <tbody>{shown.map(d => (
              <tr key={d.id}>
                <td data-label="#">{d.id}</td><td data-label="Date">{fmtDate(d.createdAt)}</td><td data-label="Donor">{d.donor}</td>
                <td data-label="Food"><Link to={`/donation/${d.id}`}>{d.food}</Link> <small className="muted">({d.qty})</small></td>
                <td data-label="NGO">{d.ngo || '—'}</td><td data-label="Meals">{d.meals}</td><td data-label="Status"><StatusBadge status={d.status} /></td>
              </tr>
            ))}</tbody>
          </table>
        </div>
      )}</Async>
    </>
  );
}
