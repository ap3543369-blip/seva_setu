import { Link } from 'react-router-dom';
import { Outlet } from 'react-router-dom';
import { api } from '../api.js';
import { useFetch } from '../utils.js';
import { Spinner } from './ui.jsx';

const MESSAGE = {
  coming_soon: 'This module is switched off at the moment.',
  not_for_role: 'Your account type cannot use this module.',
  revoked: 'An admin turned this module off for your account.',
  needs_verification: 'Your account is waiting for admin verification. This module unlocks once you are verified.'
};

/** Route wrapper: renders the module's pages only when the backend says the module is available for this user. */
export default function ModuleGate({ module }) {
  const state = useFetch(() => api('/modules'));
  if (state.loading && !state.data) return <Spinner />;
  if (state.error) return <div className="alert bad">{state.error.message}</div>;
  const m = (state.data || []).find(x => x.key === module);
  if (!m || m.status !== 'available') {
    return (
      <div className="card gate">
        <div className="empty-icon">{m?.icon || '🔒'}</div>
        <h2>{m?.label || 'Module'} is not available</h2>
        <p className="muted">{MESSAGE[m?.status] || 'This module is not available for your account.'}</p>
        <Link className="btn btn-primary" to="/hub">Back to SevaSetu hub</Link>
      </div>
    );
  }
  return <Outlet />;
}
