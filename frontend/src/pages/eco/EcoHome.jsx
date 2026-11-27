import { Link } from 'react-router-dom';
import { api } from '../../api.js';
import { useAuth } from '../../auth.jsx';
import { fmt, monthLabel, useFetch } from '../../utils.js';
import { Async, PageHead, Stat } from '../../components/ui.jsx';
import { BarChart, Donut } from '../../components/charts.jsx';

const COLORS = ['#168A4A', '#2E7BE6', '#F5A524', '#7C4DDB', '#E5558F', '#6B7A70'];

export default function EcoHome() {
  const { user } = useAuth();
  const state = useFetch(() => api('/ecoai/dashboard'));
  return (
    <>
      <PageHead title="EcoAI" subtitle="E-waste collection, recycling and AI forecasts to plan ahead.">
        {user.role === 'donor' && <Link to="/ecoai/pickups" className="btn btn-primary">♻️ Request a pickup</Link>}
        {user.role !== 'donor' && <Link to="/ecoai/forecast" className="btn btn-primary">🔮 Open forecast</Link>}
      </PageHead>
      <Async state={state}>{d => user.role === 'donor' ? (
        <div className="stat-grid four">
          <Stat icon="📮" label="Pickup requests" value={fmt(d.pickups)} />
          <Stat icon="⏳" label="Open" value={fmt(d.open)} tone="orange" />
          <Stat icon="✅" label="Collected" value={fmt(d.collected)} tone="blue" />
          <Stat icon="♻️" label="kg recycled" value={fmt(d.kgRecycled)} tone="purple" />
        </div>
      ) : (
        <>
          {d.demoData && <div className="alert info">ℹ️ Part of this data is generated demo data (marked “demo” under Records).</div>}
          <div className="stat-grid four">
            <Stat icon="⚖️" label="Total kg recorded" value={fmt(d.totalKg)} />
            <Stat icon="🚚" label="Scheduled pickups" value={fmt(d.scheduled)} tone="orange" />
            <Stat icon="✅" label="Collected pickups" value={fmt(d.collected)} tone="blue" />
            <Stat icon="📮" label="Open requests" value={fmt(d.openRequests)} tone="purple" />
          </div>
          <div className="two-col">
            <div className="card"><h3>Collected per month (kg)</h3>{d.series.length ? <BarChart values={d.series} labels={d.months.map(m => monthLabel(m).split(' ')[0])} height={210} unit=" kg" /> : <p className="muted">No records yet. Add months under Records.</p>}</div>
            <div className="card"><h3>By category</h3>{d.byCategory.length ? <Donut center={fmt(d.totalKg)} segments={d.byCategory.map((c, i) => ({ label: c.category, value: Math.round(c.kg), color: COLORS[i % COLORS.length] }))} /> : <p className="muted">No data yet.</p>}</div>
          </div>
          <p className="muted small">Months of history: {d.monthsOfHistory}. The forecast needs at least 4 months.</p>
        </>
      )}</Async>
    </>
  );
}
