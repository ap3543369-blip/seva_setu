import { useState } from 'react';
import { api } from '../api.js';
import { fmt, useFetch } from '../utils.js';
import { Async, Avatar, Stat, Tabs } from '../components/ui.jsx';
import { LineChart, BarChart } from '../components/charts.jsx';

export default function Impact() {
  const [range, setRange] = useState('all');
  const impact = useFetch(() => api(`/impact?range=${range}`), [range]);
  const top = useFetch(() => api('/impact/leaderboard'));
  const stories = useFetch(() => api('/stories'));
  return (
    <div className="container page-pad">
      <div className="section-head">
        <span className="eyebrow">Our impact</span>
        <h1>Real change through shared food</h1>
        <p>Live numbers calculated from every delivered donation on FoodShare.</p>
      </div>
      <div className="center"><Tabs value={range} onChange={setRange} tabs={[{ key: 'month', label: 'This month' }, { key: 'year', label: 'This year' }, { key: 'all', label: 'All time' }]} /></div>
      <Async state={impact}>{d => (
        <>
          <div className="stat-grid">
            <Stat icon="🍽️" label="Meals donated" value={fmt(d.meals)} />
            <Stat icon="🧑‍🤝‍🧑" label="People helped" value={fmt(d.people)} tone="orange" />
            <Stat icon="🌍" label="kg CO₂ reduced" value={fmt(d.co2)} tone="blue" />
            <Stat icon="🏆" label="Active donors" value={fmt(d.donors)} tone="purple" />
            <Stat icon="🏢" label="Partner NGOs" value={fmt(d.ngos)} tone="pink" />
          </div>
          <div className="card">
            <h3>Meals donated over time <small className="muted">· {d.caption}</small></h3>
            {d.series.length > 6 || range === 'all' ? <LineChart values={d.series} labels={d.labels} /> : <BarChart values={d.series} labels={d.labels} />}
          </div>
        </>
      )}</Async>
      <div className="two-col top-gap">
        <div className="card">
          <h3>🏆 Top donors</h3>
          <Async state={top}>{rows => rows.length ? (
            <ol className="leader">{rows.map((t, i) => <li key={t.name}><span className="rank">{i + 1}</span><Avatar name={t.name} /><div><strong>{t.name}</strong><small>{t.donations} donations</small></div><b>{fmt(t.meals)} meals</b></li>)}</ol>
          ) : <p className="muted">No delivered donations yet.</p>}</Async>
        </div>
        <div className="card">
          <h3>💬 Success stories</h3>
          <Async state={stories}>{rows => rows.map(s => (
            <blockquote key={s.id} className="quote"><p>“{s.quote}”</p><footer><Avatar name={s.name} size={32} /> <strong>{s.name}</strong> <small>{s.role}</small></footer></blockquote>
          ))}</Async>
        </div>
      </div>
    </div>
  );
}
