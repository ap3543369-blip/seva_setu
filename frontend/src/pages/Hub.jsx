import { Link } from 'react-router-dom';
import { api } from '../api.js';
import { useAuth } from '../auth.jsx';
import { useFetch } from '../utils.js';
import { Async, PageHead } from '../components/ui.jsx';
import TiltCard from '../components/TiltCard.jsx';

const STATUS = {
  available: ['Open', 'ok'],
  coming_soon: ['Coming soon', 'info'],
  not_for_role: ['Not for your account type', 'warn'],
  revoked: ['Turned off by admin', 'bad'],
  needs_verification: ['Awaiting admin verification', 'warn']
};

/** Where a module opens for a role. */
const homeFor = (key, role) => {
  if (key === 'foodshare') return { donor: '/donor', ngo: '/ngo', admin: '/admin' }[role] || null;
  return { bookshare: '/books', medishare: '/medishare', ecoai: '/ecoai' }[key] || null;
};

/** /hub = the SevaSetu module launcher. */
export default function Hub() {
  const { user } = useAuth();
  const state = useFetch(() => api('/modules'));
  return (
    <div className="container page-pad">
      <div className="section-head">
        <span className="eyebrow">SevaSetu hub</span>
        <h1>{`Welcome, ${user.name}`}</h1>
        <p>Pick a module. What you can open depends on your account type and admin verification.</p>
      </div>
      <Async state={state}>{mods => (
        <div className="grid-4">
          {mods.map(m => {
            const [label, tone] = STATUS[m.status] || ['—', 'info'];
            const href = m.status === 'available' ? homeFor(m.key, user.role) : null;
            const live = m.status === 'available' && href;
            return (
              <TiltCard key={m.key} className={`module-card ${live ? '' : 'locked'}`}>
                <span className="module-icon">{m.icon}</span>
                <h3>{m.label}</h3>
                <p>{m.description}</p>
                <span className={`badge ${tone}`}>{label}</span>
                {live && <Link className="btn btn-primary btn-block" to={href}>Open {m.label}</Link>}
              </TiltCard>
            );
          })}
        </div>
      )}</Async>
    </div>
  );
}
