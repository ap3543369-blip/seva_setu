import { api } from '../../api.js';
import { downloadCsv, fmt, fmtDateTime, useFetch } from '../../utils.js';
import { Async, PageHead, Stat } from '../../components/ui.jsx';
import { Donut } from '../../components/charts.jsx';

const COLORS = ['#168A4A', '#2E7BE6', '#F5A524', '#7C4DDB', '#E5558F', '#6B7A70', '#E5484D'];
const segs = obj => Object.entries(obj || {}).map(([label, value], i) => ({ label, value, color: COLORS[i % COLORS.length] }));
const sum = obj => Object.values(obj || {}).reduce((a, b) => a + b, 0);

export default function AdminReports() {
  const state = useFetch(() => api('/admin/reports'));
  const exportCsv = r => {
    const rows = [['Section', 'Metric', 'Value']];
    Object.entries(r.users.byRole).forEach(([k, v]) => rows.push(['Users', k, v]));
    rows.push(['Users', 'pending verification', r.users.pendingVerification]);
    const flat = (name, o) => Object.entries(o).forEach(([k, v]) => (typeof v === 'object' ? Object.entries(v).forEach(([a, b]) => rows.push([name, `${k}: ${a}`, b])) : rows.push([name, k, v])));
    flat('FoodShare', r.foodshare); flat('BookShare', r.bookshare); flat('MediShare', r.medishare); flat('EcoAI', r.ecoai);
    downloadCsv('sevasetu-report.csv', rows);
  };
  return (
    <>
      <PageHead title="Reports" subtitle="One view across all four modules (counts only).">
        {state.data && <button className="btn btn-ghost" onClick={() => exportCsv(state.data)}>⬇ Export CSV</button>}
      </PageHead>
      <Async state={state}>{r => (
        <>
          <p className="muted small">Generated {fmtDateTime(r.generatedAt)}</p>
          <div className="stat-grid">
            <Stat icon="🍛" label="Food donations" value={fmt(r.foodshare.donations)} />
            <Stat icon="📚" label="Book listings" value={fmt(r.bookshare.listings)} tone="blue" hint={`${fmt(r.bookshare.copiesHandedOver)} copies handed over`} />
            <Stat icon="💊" label="Medicine listings" value={fmt(r.medishare.donations)} tone="orange" hint={`${fmt(r.medishare.auditEntries)} audit entries`} />
            <Stat icon="♻️" label="kg e-waste recorded" value={fmt(r.ecoai.kgRecorded)} tone="purple" hint={r.ecoai.includesDemoData ? 'includes demo data' : undefined} />
            <Stat icon="⏳" label="Awaiting verification" value={fmt(r.users.pendingVerification)} tone="pink" />
          </div>
          <div className="two-col">
            <div className="card"><h3>Users by role</h3><Donut segments={segs(r.users.byRole)} center={sum(r.users.byRole)} /></div>
            <div className="card"><h3>FoodShare donations by status</h3><Donut segments={segs(r.foodshare.byStatus)} center={sum(r.foodshare.byStatus)} /></div>
            <div className="card"><h3>BookShare listings by status</h3><Donut segments={segs(r.bookshare.listingsByStatus)} center={sum(r.bookshare.listingsByStatus)} /></div>
            <div className="card"><h3>BookShare requests by status</h3><Donut segments={segs(r.bookshare.requestsByStatus)} center={sum(r.bookshare.requestsByStatus)} /></div>
            <div className="card"><h3>MediShare listings by status</h3><Donut segments={segs(r.medishare.donationsByStatus)} center={sum(r.medishare.donationsByStatus)} /></div>
            <div className="card"><h3>MediShare requests by status</h3><Donut segments={segs(r.medishare.requestsByStatus)} center={sum(r.medishare.requestsByStatus)} /></div>
            <div className="card"><h3>E-waste pickups by status</h3><Donut segments={segs(r.ecoai.pickupsByStatus)} center={sum(r.ecoai.pickupsByStatus)} /></div>
            <div className="card"><h3>EcoAI data</h3><p>{fmt(r.ecoai.monthsOfData)} month(s) of records · {fmt(r.ecoai.pickups)} pickups</p><p className="muted small">The platform-wide forecast uses all recyclers’ records.</p></div>
          </div>
        </>
      )}</Async>
    </>
  );
}
