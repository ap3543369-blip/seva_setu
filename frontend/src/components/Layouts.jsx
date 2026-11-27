import { useEffect, useState } from 'react';
import { Link, NavLink, Navigate, Outlet, useLocation, useNavigate } from 'react-router-dom';
import { useAuth, dashboardPath, VERIFY_ROLES } from '../auth.jsx';
import { api } from '../api.js';
import { Avatar, Spinner } from './ui.jsx';
import NgoPending from '../pages/ngo/NgoPending.jsx';

export const Logo = ({ light }) => (
  <Link to="/" className={`logo ${light ? 'light' : ''}`}>
    <img src="/assets/icons/logo.svg" alt="" width="34" height="34" />
    <span>SevaSetu</span>
  </Link>
);

/* ------------------------------------------------------------ public site */
export function PublicLayout() {
  const { user, logout } = useAuth();
  const [open, setOpen] = useState(false);
  const [scrolled, setScrolled] = useState(false);
  const loc = useLocation();
  useEffect(() => { setOpen(false); window.scrollTo(0, 0); }, [loc.pathname]);
  useEffect(() => {
    const on = () => setScrolled(window.scrollY > 20);
    on(); window.addEventListener('scroll', on, { passive: true });
    return () => window.removeEventListener('scroll', on);
  }, []);
  const links = [['/', 'Home'], ['/ngos', 'Partner NGOs'], ['/impact', 'Impact'], ['/contact', 'Contact']];
  return (
    <>
      <header className={`site-nav ${scrolled ? 'scrolled' : ''} ${loc.pathname === '/' ? 'on-hero' : ''}`}>
        <div className="container nav-row">
          <Logo />
          <button className="icon-btn nav-toggle" aria-label="Menu" aria-expanded={open} onClick={() => setOpen(o => !o)}>{open ? '✕' : '☰'}</button>
          <nav className={`nav-links ${open ? 'open' : ''}`}>
            {links.map(([to, label]) => <NavLink key={to} to={to} end className={({ isActive }) => (isActive ? 'active' : '')}>{label}</NavLink>)}
            <span className="nav-spacer" />
            {user ? (
              <>
                <Link className="btn btn-primary btn-sm" to={dashboardPath(user.role)}>Dashboard</Link>
                <button className="btn btn-ghost btn-sm" onClick={logout}>Logout</button>
              </>
            ) : (
              <>
                <Link className="btn btn-ghost btn-sm" to="/login">Login</Link>
                <Link className="btn btn-primary btn-sm" to="/signup">Sign up free</Link>
              </>
            )}
          </nav>
        </div>
      </header>
      <main><Outlet /></main>
      <Footer />
    </>
  );
}

function Footer() {
  return (
    <footer className="site-footer">
      <div className="container foot-grid">
        <div>
          <Logo light />
          <p><strong>Connecting Resources. Empowering Lives.</strong><br />One platform, multiple ways to help — food, books, medicines and e-waste.</p>
        </div>
        <div><h4>Platform</h4><Link to="/hub">SevaSetu hub</Link><Link to="/signup">Start donating</Link><Link to="/register/ngo">Join as NGO</Link><Link to="/impact">Our impact</Link></div>
        <div><h4>Company</h4><Link to="/ngos">Partner NGOs</Link><Link to="/contact">Contact</Link><Link to="/login">Login</Link></div>
        <div><h4>Say hello</h4><span>hello@foodshare.example</span><span>Delhi, India</span></div>
      </div>
      <div className="container foot-bottom">© {new Date().getFullYear()} SevaSetu. One platform, multiple ways to help.</div>
    </footer>
  );
}

/* ------------------------------------------------------------ dashboards */
const COMMON = [['/notifications', '🔔', 'Notifications'], ['/profile', '👤', 'Profile'], ['/settings', '⚙️', 'Settings']];
const NAV = {
  donor: [['/donor', '🏠', 'Dashboard', true], ['/donor/donate', '➕', 'Donate Food'], ['/donor/donations', '📦', 'My Donations'], ['/books/donate', '📚', 'Donate Books'], ['/books/mine', '📖', 'My Books'], ['/ecoai/pickups', '♻️', 'E-waste Pickup'], ['/donor/rewards', '🎁', 'Rewards'], ['/donor/notifications', '🔔', 'Notifications'], ['/donor/profile', '👤', 'Profile'], ['/donor/settings', '⚙️', 'Settings']],
  ngo: [['/ngo', '🏠', 'Dashboard', true], ['/ngo/browse', '🔎', 'Find Food'], ['/ngo/requests', '📋', 'My Requests'], ['/books', '📚', 'BookShare', true], ['/books/requests', '📋', 'Book Requests'], ['/medishare/browse', '💊', 'Find Medicines'], ['/medishare/requests', '🧾', 'Medicine Requests'], ['/ngo/notifications', '🔔', 'Notifications'], ['/ngo/profile', '👤', 'Profile'], ['/ngo/settings', '⚙️', 'Settings']],
  admin: [['/admin', '📊', 'Overview', true], ['/admin/approvals', '🛎️', 'Approvals'], ['/admin/users', '👥', 'Users'], ['/admin/ngos', '✅', 'Verification'], ['/admin/modules', '🧩', 'Modules'], ['/admin/donations', '📦', 'Food Donations'], ['/admin/medishare', '💊', 'MediShare Control'], ['/ecoai', '♻️', 'EcoAI', true], ['/ecoai/forecast', '🔮', 'EcoAI Forecast'], ['/admin/reports', '📈', 'Reports'], ['/admin/messages', '✉️', 'Messages'], ['/notifications', '🔔', 'Notifications'], ['/admin/settings', '⚙️', 'Settings']],
  recipient: [['/books', '📚', 'Browse Books', true], ['/books/requests', '📋', 'My Requests'], ...COMMON],
  hospital: [['/medishare', '🏥', 'Dashboard', true], ['/medishare/donate', '➕', 'Donate Items'], ['/medishare/mine', '📦', 'My Listings'], ['/medishare/browse', '🔎', 'Find Items'], ['/medishare/requests', '📋', 'My Requests'], ...COMMON],
  recycler: [['/ecoai', '📊', 'Dashboard', true], ['/ecoai/pickups', '🚚', 'Pickups'], ['/ecoai/records', '🗂️', 'Records'], ['/ecoai/forecast', '🔮', 'Forecast'], ...COMMON]
};
const ROLE_LABEL = { donor: 'Donor', ngo: 'NGO', admin: 'Admin', recipient: 'Recipient', hospital: 'Hospital / Pharmacy', recycler: 'Recycler' };
/** donor / ngo keep their own notification, profile and settings routes; every other role uses the shared ones. */
const ownRoutes = role => role === 'donor' || role === 'ngo';

/** Shell for every logged-in module page: sidebar by role, any role may enter. */
export function AppShell() {
  const { user } = useAuth();
  const loc = useLocation();
  if (user === undefined) return <div className="page-center"><Spinner label="Checking your session…" /></div>;
  if (!user) return <Navigate to={`/login?next=${encodeURIComponent(loc.pathname)}`} replace />;
  if (user.role === 'ngo' && !user.verified) return <NgoPending />; // an NGO sees nothing else until the admin verifies it
  return <DashboardLayout role={user.role} />;
}

/** Any logged-in user may enter (used for the hub and module pages). */
export function RequireLogin() {
  const { user } = useAuth();
  const loc = useLocation();
  if (user === undefined) return <div className="page-center"><Spinner label="Checking your session…" /></div>;
  if (!user) return <Navigate to={`/login?next=${encodeURIComponent(loc.pathname)}`} replace />;
  return <Outlet />;
}

/** Wrap a route group: only the given role may enter, everyone else is sent to login / their own dashboard. */
export function RequireRole({ role }) {
  const { user } = useAuth();
  const loc = useLocation();
  if (user === undefined) return <div className="page-center"><Spinner label="Checking your session…" /></div>;
  if (!user) return <Navigate to={`/login?next=${encodeURIComponent(loc.pathname)}`} replace />;
  if (user.role !== role) return <Navigate to={dashboardPath(user.role)} replace />;
  if (user.role === 'ngo' && !user.verified) return <NgoPending />;
  return <DashboardLayout role={role} />;
}

function DashboardLayout({ role }) {
  const { user, logout } = useAuth();
  const nav = useNavigate();
  const loc = useLocation();
  const [open, setOpen] = useState(false);
  const [unread, setUnread] = useState(0);
  useEffect(() => setOpen(false), [loc.pathname]);
  useEffect(() => {
    let live = true;
    const poll = () => api('/notifications/unread-count').then(r => live && setUnread(r.unread)).catch(() => {});
    poll(); const id = setInterval(poll, 30000);
    const onRead = () => setUnread(0);
    window.addEventListener('notifications-read', onRead);
    return () => { live = false; clearInterval(id); window.removeEventListener('notifications-read', onRead); };
  }, [role, loc.pathname]);
  const [approvals, setApprovals] = useState(0);
  useEffect(() => {
    if (role !== 'admin') return undefined;
    let live = true;
    const poll = () => api('/admin/approvals/summary').then(r => live && setApprovals(r.total)).catch(() => {});
    poll(); const id = setInterval(poll, 30000);
    return () => { live = false; clearInterval(id); };
  }, [role, loc.pathname]);
  const label = ROLE_LABEL[role] || role;
  return (
    <div className="shell">
      <aside className={`sidebar ${open ? 'open' : ''}`}>
        <Logo />
        <span className={`role-pill ${role}`}>{label} panel</span>
        <nav>
          {NAV[role].map(([to, icon, text, end]) => (
            <NavLink key={to} to={to} end={!!end} className={({ isActive }) => (isActive ? 'active' : '')}>
              <span aria-hidden="true">{icon}</span>{text}
              {text === 'Notifications' && unread > 0 && <em className="count">{unread}</em>}
              {text === 'Approvals' && approvals > 0 && <em className="count">{approvals}</em>}
            </NavLink>
          ))}
        </nav>
        <div className="side-foot">
          <Link to="/hub">🧭 SevaSetu hub</Link>
          <Link to="/">← Back to website</Link>
          <button onClick={async () => { await logout(); nav('/'); }}>↩ Logout</button>
        </div>
      </aside>
      {open && <div className="scrim" onClick={() => setOpen(false)} />}
      <div className="shell-main">
        <header className="topbar">
          <button className="icon-btn menu-btn" aria-label="Open menu" onClick={() => setOpen(true)}>☰</button>
          <div className="topbar-spacer" />
          {(
            <Link to={ownRoutes(role) ? `/${role}/notifications` : '/notifications'} className="icon-btn bell" aria-label={`Notifications (${unread} unread)`}>🔔{unread > 0 && <em className="count">{unread}</em>}</Link>
          )}
          <Link to={ownRoutes(role) ? `/${role}/profile` : '/profile'} className="who">
            <Avatar name={user.name} size={36} />
            <span><strong>{user.name}</strong><small>{label}{VERIFY_ROLES.includes(role) && !user.verified ? ' · pending verification' : ''}</small></span>
          </Link>
        </header>
        <div className="content"><Outlet /></div>
      </div>
    </div>
  );
}
