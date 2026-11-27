import { useState } from 'react';
import { Link } from 'react-router-dom';
import { api } from '../api.js';
import { fmt, useFetch } from '../utils.js';
import { Async, Avatar, Empty } from '../components/ui.jsx';
import TiltCard from '../components/TiltCard.jsx';

export default function Ngos() {
  const state = useFetch(() => api('/ngos'));
  const [q, setQ] = useState('');
  return (
    <div className="container page-pad">
      <div className="section-head">
        <span className="eyebrow">Partner NGOs</span>
        <h1>Organisations turning food into hope</h1>
        <p>Every NGO here is verified by our team before it can request donations.</p>
      </div>
      <div className="center"><input className="search" placeholder="🔎  Search NGOs…" value={q} onChange={e => setQ(e.target.value)} aria-label="Search NGOs" /></div>
      <Async state={state}>{rows => {
        const list = rows.filter(n => `${n.name} ${n.orgType}`.toLowerCase().includes(q.toLowerCase()));
        if (!list.length) return <Empty icon="🏢" title="No NGOs found">Try a different search.</Empty>;
        return (
          <div className="grid-3">
            {list.map(n => (
              <TiltCard key={n.id} className="card ngo-card">
                <Avatar name={n.name} size={56} />
                <h3>{n.name}</h3>
                <span className="chip">{n.orgType || 'NGO'}</span>
                <div className="ngo-nums"><div><b>{fmt(n.fulfilled)}</b><small>Pickups</small></div><div><b>{fmt(n.meals)}</b><small>Meals</small></div><div><b>{fmt(n.partners)}</b><small>Donors</small></div></div>
              </TiltCard>
            ))}
          </div>
        );
      }}</Async>
      <div className="center top-gap"><Link to="/register/ngo" className="btn btn-primary">Register your NGO</Link></div>
    </div>
  );
}
