import { useEffect, useRef, useState } from 'react';
import { Link } from 'react-router-dom';
import { api } from '../api.js';
import { useAuth, dashboardPath } from '../auth.jsx';
import { fmt, useFetch } from '../utils.js';
import Hero3D from '../components/Hero3D.jsx';
import TiltCard from '../components/TiltCard.jsx';
import FoodCard from '../components/FoodCard.jsx';
import { Avatar } from '../components/ui.jsx';

/** Fades children in when they scroll into view. */
function Reveal({ children, delay = 0, className = '' }) {
  const ref = useRef(null);
  const [on, setOn] = useState(false);
  useEffect(() => {
    const el = ref.current;
    if (!el || !('IntersectionObserver' in window)) { setOn(true); return undefined; }
    const io = new IntersectionObserver(([e]) => { if (e.isIntersecting) { setOn(true); io.disconnect(); } }, { threshold: 0.15 });
    io.observe(el);
    return () => io.disconnect();
  }, []);
  return <div ref={ref} className={`reveal ${on ? 'in' : ''} ${className}`} style={{ transitionDelay: `${delay}ms` }}>{children}</div>;
}

/** Counts up to `to` once visible. */
function Counter({ to, suffix = '' }) {
  const ref = useRef(null);
  const [v, setV] = useState(0);
  useEffect(() => {
    const el = ref.current; let raf;
    const run = () => {
      const t0 = performance.now();
      const step = now => { const p = Math.min(1, (now - t0) / 1400); setV(Math.round(to * (1 - Math.pow(1 - p, 3)))); if (p < 1) raf = requestAnimationFrame(step); };
      raf = requestAnimationFrame(step);
    };
    if (!('IntersectionObserver' in window)) { setV(to); return undefined; }
    const io = new IntersectionObserver(([e]) => { if (e.isIntersecting) { run(); io.disconnect(); } });
    io.observe(el);
    return () => { io.disconnect(); cancelAnimationFrame(raf); };
  }, [to]);
  return <span ref={ref}>{fmt(v)}{suffix}</span>;
}

const STEPS = [
  { icon: '📸', title: 'Post surplus food', text: 'Restaurants, messes and households add photos, quantity and a pickup window in under a minute.' },
  { icon: '🤝', title: 'NGO requests & collects', text: 'Verified NGOs nearby see it instantly, send a request and pick it up before it spoils.' },
  { icon: '🎁', title: 'Earn rewards', text: 'Every delivered donation earns points that turn into gift cards from brands you love.' }
];
const ROLES = {
  donor: { label: 'Donors', color: '#168A4A', points: ['4-step donate form with photo upload', 'Live status timeline for every donation', 'Points, rewards and gift-card codes', 'Printable donation certificates', 'Leaderboard & personal impact numbers'] },
  ngo: { label: 'NGOs', color: '#2E7BE6', points: ['Search & filter food near you', 'One-tap requests with a note to the donor', 'Accept → Mark delivered workflow', 'Weekly meals chart and request board', 'Address & phone unlock after acceptance'] },
  admin: { label: 'Admins', color: '#7C4DDB', points: ['Platform stats and live charts', 'Verify NGOs before they can request', 'Block / unblock accounts', 'Moderate every donation', 'Inbox for contact messages + CSV export'] }
};
const GIFTS = [['amazon', '₹100', '500 pts'], ['flipkart', '₹250', '1,000 pts'], ['swiggy', '₹500', '2,000 pts'], ['zomato', '₹500', '2,000 pts'], ['bigbasket', '₹1,000', '3,500 pts']];
const FAQ = [
  ['Is FoodShare free?', 'Yes. Donating, requesting and earning rewards are completely free for donors and NGOs.'],
  ['How do NGOs get verified?', 'New NGO accounts are reviewed by an admin. Once verified, they can request donations from the live list.'],
  ['Is it safe to share my address?', 'Your exact address and phone number are only shown to the NGO after you accept their request.'],
  ['What food can I donate?', 'Cooked food, packed food, bakery items, fruits & vegetables and raw ingredients — as long as it is fresh and safely packed.']
];

export default function Landing() {
  const { user } = useAuth();
  const feed = useFetch(() => api('/donations/available'));
  const impact = useFetch(() => api('/impact?range=all'));
  const stories = useFetch(() => api('/stories'));
  const top = useFetch(() => api('/impact/leaderboard'));
  const mods = useFetch(() => api('/public/modules'));
  const [role, setRole] = useState('donor');
  const [story, setStory] = useState(0);
  const [faq, setFaq] = useState(0);
  const live = feed.data || [];
  const im = impact.data;
  const st = stories.data || [];

  useEffect(() => {
    if (st.length < 2) return undefined;
    const id = setInterval(() => setStory(s => (s + 1) % st.length), 5000);
    return () => clearInterval(id);
  }, [st.length]);

  const cta = user
    ? <Link className="btn btn-primary btn-lg" to={dashboardPath(user.role)}>Go to my dashboard →</Link>
    : <><Link className="btn btn-primary btn-lg" to="/signup">Donate food →</Link><Link className="btn btn-light btn-lg" to="/register/ngo">Join as NGO</Link></>;

  return (
    <>
      {/* ---------------------------------------------------------------- hero */}
      <section className="hero">
        <div className="hero-bg" aria-hidden="true"><i /><i /><i /></div>
        <div className="container hero-grid">
          <div className="hero-copy">
            <span className="eyebrow">🌍 One platform, multiple ways to help</span>
            <h1>Connecting Resources. <span className="grad">Empowering Lives.</span></h1>
            <p className="lead">SevaSetu connects donors, NGOs, hospitals, recipients and recyclers — so surplus food, books, medicines and e-waste reach the people and places that need them.</p>
            <div className="hero-cta">{cta}</div>
            <ul className="hero-trust">
              <li>✔ 100% free</li><li>✔ Verified NGOs</li><li>✔ Real-time tracking</li>
            </ul>
          </div>
          <div className="hero-visual">
            <Hero3D donations={live} />
            <TiltCard className="float-card fc1"><span>🍛</span><div><strong>Donate food</strong><small>Prevent good food going to waste</small></div></TiltCard>
            <TiltCard className="float-card fc2"><span>🎁</span><div><strong>Earn rewards</strong><small>Gift cards for every donation</small></div></TiltCard>
            <TiltCard className="float-card fc3"><span>🧑‍🤝‍🧑</span><div><strong>Help people</strong><small>Support communities in need</small></div></TiltCard>
            <span className="drag-hint">Drag the globe · one pin per live donation</span>
          </div>
        </div>
        <div className="container">
          <div className="hero-stats">
            {[['🍽️', im?.meals ?? 0, 'Meals donated'], ['🧑‍🤝‍🧑', im?.people ?? 0, 'People helped'], ['🌍', im?.co2 ?? 0, 'kg CO₂ reduced'], ['🏢', im?.ngos ?? 0, 'Partner NGOs'], ['🏆', im?.donors ?? 0, 'Active donors']].map(([i, n, l]) => (
              <div key={l}><span>{i}</span><b><Counter to={n} /></b><small>{l}</small></div>
            ))}
          </div>
        </div>
      </section>

      {/* ---------------------------------------------------------------- modules */}
      <section className="section">
        <div className="container">
          <Reveal className="section-head"><span className="eyebrow">Four modules</span><h2>One platform, multiple ways to help</h2><p>Modules open one by one as they are ready and verified.</p></Reveal>
          <div className="grid-4">
            {(mods.data?.modules || []).map((m, i) => (
              <Reveal key={m.key} delay={i * 100}>
                <TiltCard className="module-card">
                  <span className="module-icon">{m.icon}</span>
                  <h3>{m.label}</h3>
                  <p>{m.description}</p>
                  <span className={`badge ${m.enabled ? 'ok' : 'info'}`}>{m.enabled ? 'Live' : 'Coming soon'}</span>
                </TiltCard>
              </Reveal>
            ))}
          </div>
        </div>
      </section>

      {/* ---------------------------------------------------------------- how it works */}
      <section className="section">
        <div className="container">
          <Reveal className="section-head"><span className="eyebrow">How FoodShare works</span><h2>From surplus to a full plate in 3 steps</h2></Reveal>
          <div className="grid-3 steps">
            {STEPS.map((s, i) => (
              <Reveal key={s.title} delay={i * 120}>
                <TiltCard className="step-card"><span className="step-num">{i + 1}</span><div className="step-icon">{s.icon}</div><h3>{s.title}</h3><p>{s.text}</p></TiltCard>
              </Reveal>
            ))}
          </div>
        </div>
      </section>

      {/* ---------------------------------------------------------------- live feed */}
      <section className="section alt">
        <div className="container">
          <Reveal className="section-head"><span className="eyebrow"><i className="live-dot" /> FoodShare · live now</span><h2>Food waiting to be rescued</h2><p>Real donations from our community, updated as they come in.</p></Reveal>
          {live.length === 0 ? (
            <p className="center-note">{feed.loading ? 'Loading donations…' : 'No open donations right now — be the first to donate!'}</p>
          ) : (
            <div className="grid-3">{live.slice(0, 6).map((d, i) => <Reveal key={d.id} delay={i * 80}><FoodCard d={d} compact /></Reveal>)}</div>
          )}
          <div className="center"><Link className="btn btn-primary" to={user?.role === 'ngo' ? '/ngo/browse' : '/register/ngo'}>{user?.role === 'ngo' ? 'Browse all food' : 'Sign up as NGO to request'}</Link></div>
        </div>
      </section>

      {/* ---------------------------------------------------------------- roles */}
      <section className="section">
        <div className="container">
          <Reveal className="section-head"><span className="eyebrow">Built for everyone involved</span><h2>One platform, three powerful dashboards</h2></Reveal>
          <div className="role-switch">
            {Object.entries(ROLES).map(([k, r]) => <button key={k} className={role === k ? 'active' : ''} style={{ '--c': r.color }} onClick={() => setRole(k)}>{r.label}</button>)}
          </div>
          <div className="role-panel" style={{ '--c': ROLES[role].color }} key={role}>
            <ul>{ROLES[role].points.map(p => <li key={p}>✔ {p}</li>)}</ul>
            <TiltCard className="mock">
              <div className="mock-bar"><i /><i /><i /></div>
              <div className="mock-row"><b /><b /><b /></div>
              <div className="mock-chart">{[40, 70, 55, 90, 65, 100, 80].map((h, i) => <span key={i} style={{ height: `${h}%` }} />)}</div>
              <div className="mock-lines"><i /><i /><i /></div>
            </TiltCard>
          </div>
        </div>
      </section>

      {/* ---------------------------------------------------------------- rewards */}
      <section className="section dark">
        <div className="container rewards-teaser">
          <Reveal>
            <span className="eyebrow light">Rewards</span>
            <h2>Do good. Get rewarded.</h2>
            <p>Earn points for every kilo you donate and redeem them for gift cards from your favourite brands.</p>
            <Link className="btn btn-light" to="/signup">Start earning</Link>
          </Reveal>
          <div className="gift-stack">
            {GIFTS.map(([b, amt, pts], i) => (
              <TiltCard key={b} className="gift" style={{ '--i': i }}><img src={`/assets/images/rewards/${b}.svg`} alt={b} /><strong>{amt}</strong><small>{pts}</small></TiltCard>
            ))}
          </div>
        </div>
      </section>

      {/* ---------------------------------------------------------------- leaderboard + stories */}
      <section className="section">
        <div className="container two-col">
          <div>
            <Reveal className="section-head left"><span className="eyebrow">Top donors</span><h2>Our food heroes</h2></Reveal>
            <ol className="leader">
              {(top.data || []).slice(0, 5).map((t, i) => (
                <li key={t.name}><span className="rank">{i + 1}</span><Avatar name={t.name} /><div><strong>{t.name}</strong><small>{t.donations} donations</small></div><b>{fmt(t.meals)} meals</b></li>
              ))}
              {!top.data?.length && <li className="muted">Leaderboard appears after the first delivered donations.</li>}
            </ol>
          </div>
          <div>
            <Reveal className="section-head left"><span className="eyebrow">Stories</span><h2>Voices from the community</h2></Reveal>
            {st.length > 0 && (
              <TiltCard className="story" max={4}>
                <p>“{st[story].quote}”</p>
                <div className="story-by"><Avatar name={st[story].name} size={44} /><div><strong>{st[story].name}</strong><small>{st[story].role}</small></div></div>
                <div className="dots">{st.map((s, i) => <button key={s.id} aria-label={`Story ${i + 1}`} className={i === story ? 'on' : ''} onClick={() => setStory(i)} />)}</div>
              </TiltCard>
            )}
          </div>
        </div>
      </section>

      {/* ---------------------------------------------------------------- FAQ */}
      <section className="section alt">
        <div className="container narrow">
          <Reveal className="section-head"><span className="eyebrow">FAQ</span><h2>Questions, answered</h2></Reveal>
          {FAQ.map(([q, a], i) => (
            <div key={q} className={`faq ${faq === i ? 'open' : ''}`}>
              <button onClick={() => setFaq(faq === i ? -1 : i)} aria-expanded={faq === i}>{q}<span>{faq === i ? '−' : '+'}</span></button>
              {faq === i && <p>{a}</p>}
            </div>
          ))}
        </div>
      </section>

      {/* ---------------------------------------------------------------- CTA */}
      <section className="cta-band">
        <div className="container">
          <h2>Ready to turn surplus into smiles?</h2>
          <p>Join donors and NGOs who are already making a difference — it takes a minute to start.</p>
          <div className="hero-cta center">{cta}</div>
        </div>
      </section>
    </>
  );
}
