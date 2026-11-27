import { useEffect } from 'react';
import { Link, useNavigate } from 'react-router-dom';
import { api } from '../../api.js';
import { useAuth } from '../../auth.jsx';
import { fmtDate, useFetch } from '../../utils.js';
import { Spinner } from '../../components/ui.jsx';

/** Everything an unverified NGO sees instead of the app: where the application stands and what to do next. */
export default function NgoPending() {
  const { user, setUser, logout } = useAuth();
  const nav = useNavigate();
  const state = useFetch(() => api('/ngo/application'));
  // as soon as the admin verifies the NGO, the app opens by itself (no need to log in again)
  const refresh = () => api('/auth/me').then(u => { if (u.verified) setUser(u); }).catch(() => {});
  useEffect(() => { const id = setInterval(refresh, 20000); return () => clearInterval(id); }, []); // eslint-disable-line react-hooks/exhaustive-deps
  const a = state.data;
  const missing = state.error?.status === 404; // older account: no registration details yet
  const rejected = a?.status === 'REJECTED';
  return (
    <div className="pending-page">
      <header className="pending-top">
        <Link to="/" className="logo"><img src="/assets/icons/logo.svg" alt="" width="32" height="32" /><span>SevaSetu</span></Link>
        <button className="btn btn-ghost btn-sm" onClick={async () => { await logout(); nav('/'); }}>Logout</button>
      </header>
      <div className="container pending-body">
        {state.loading && !state.data && !state.error ? <Spinner /> : (
          <div className="card pending-card">
            <div className="pending-icon" aria-hidden="true">{rejected ? '✏️' : missing ? '📝' : '⏳'}</div>
            <h1>{rejected ? 'Your application needs changes' : missing ? 'Complete your NGO registration' : 'Your NGO is under review'}</h1>
            <p className="muted">
              {rejected ? 'Please fix the points below and send your application again.'
                : missing ? 'We need your organisation details and registration certificate before you can use SevaSetu.'
                  : `Thank you, ${user.name}. An admin is checking your details and certificate. You can use SevaSetu as soon as you are verified.`}
            </p>
            {rejected && a.reviewNote && <div className="alert bad"><span><strong>Admin’s note:</strong> {a.reviewNote}</span></div>}
            {a && (
              <ol className="timeline">
                <li className="done"><span>✓</span>Submitted<small>{fmtDate(a.submittedAt)}</small></li>
                <li className={rejected ? '' : 'done'}><span>{rejected ? '!' : '✓'}</span>{rejected ? 'Changes needed' : 'Under review'}</li>
                <li><span>3</span>Verified</li>
              </ol>
            )}
            {a && (
              <dl className="facts">
                <div><dt>Organisation</dt><dd>{a.orgName} ({a.orgKind})</dd></div>
                <div><dt>Registration no.</dt><dd>{a.registrationNumber}</dd></div>
                <div><dt>Contact</dt><dd>{a.contactPerson}, {a.contactPhone}</dd></div>
                <div><dt>City</dt><dd>{a.city}, {a.state}</dd></div>
                <div><dt>Certificate</dt><dd>{a.hasDocument ? <a href="/api/ngo/application/document" target="_blank" rel="noreferrer">View uploaded file</a> : 'Not uploaded'}</dd></div>
              </dl>
            )}
            <div className="row-gap center">
              {(rejected || missing) && <Link className="btn btn-primary" to="/register/ngo">{missing ? 'Complete registration' : 'Edit & send again'}</Link>}
              {!rejected && !missing && a && <Link className="btn btn-ghost" to="/register/ngo">Edit details</Link>}
              <button className="btn btn-ghost" onClick={() => { refresh(); state.reload(); }}>Check status</button>
            </div>
            <p className="muted small">What you cannot do until you are verified: browse donations, send requests or open any module.</p>
          </div>
        )}
      </div>
    </div>
  );
}
