import { Link } from 'react-router-dom';
import { api } from '../../api.js';
import { useAuth } from '../../auth.jsx';
import { fmt, useFetch } from '../../utils.js';
import { Async, PageHead, Stat } from '../../components/ui.jsx';

export default function MediHome() {
  const { user } = useAuth();
  const policy = useFetch(() => api('/medishare/policy'));
  const stats = useFetch(() => api('/medishare/stats'));
  const hospital = user.role === 'hospital';
  return (
    <>
      <PageHead title="MediShare" subtitle="Authorised healthcare organisations sharing eligible medicines and supplies.">
        {hospital ? <Link to="/medishare/donate" className="btn btn-primary">➕ Donate items</Link> : <Link to="/medishare/browse" className="btn btn-primary">🔎 Find items</Link>}
      </PageHead>
      <Async state={policy}>{p => (
        <div className="alert warn medi-notice"><span>⚠️ {p.notice}</span></div>
      )}</Async>
      {stats.data && (
        <div className="stat-grid four">
          {hospital ? (
            <>
              <Stat icon="📦" label="Listed now" value={fmt(stats.data.listed)} />
              <Stat icon="🕒" label="Waiting for admin review" value={fmt(stats.data.pendingReview)} tone="orange" />
              <Stat icon="🤝" label="Units allocated" value={fmt(stats.data.unitsAllocated)} tone="blue" />
              <Stat icon="📥" label="Requests to decide" value={fmt(stats.data.requestsToReview)} tone="purple" />
            </>
          ) : (
            <>
              <Stat icon="📋" label="Requests made" value={fmt(stats.data.requestsMade)} />
              <Stat icon="✅" label="Approved" value={fmt(stats.data.requestsApproved)} tone="orange" />
            </>
          )}
        </div>
      )}
      <Async state={policy}>{p => (
        <div className="two-col">
          <div className="card">
            <h3>Platform safety rules</h3>
            <ul className="rules">
              <li>Minimum <strong>{p.minShelfLifeDays} days</strong> of shelf life left</li>
              <li>{p.requireSealedPack ? 'Sealed, original-pack items only' : 'Sealed packs preferred'}</li>
              <li>{p.allowColdChain ? 'Cold-chain items allowed' : 'Cold-chain items are not accepted'}</li>
              <li>{p.requireAdminReview ? 'Every listing is reviewed by an admin before it goes live' : 'Listings go live after automatic checks'}</li>
              <li>Controlled / restricted items are blocked automatically</li>
              <li>Only verified NGOs and hospitals can request, with a registration number</li>
              <li>Every action is written to an audit trail</li>
            </ul>
          </div>
          <div className="card">
            <h3>Accepted categories</h3>
            <div className="chips">{p.allowedCategories.map(c => <span key={c} className="chip">{c}</span>)}</div>
            <h3 className="top-gap">Quick links</h3>
            <div className="row-gap">
              {hospital && <><Link className="btn btn-ghost" to="/medishare/mine">My listings</Link></>}
              <Link className="btn btn-ghost" to="/medishare/browse">Find items</Link>
              <Link className="btn btn-ghost" to="/medishare/requests">My requests</Link>
            </div>
          </div>
        </div>
      )}</Async>
    </>
  );
}
