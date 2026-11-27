import { useMemo, useState } from 'react';
import { Link, Navigate, useNavigate, useSearchParams } from 'react-router-dom';
import { useAuth, dashboardPath } from '../auth.jsx';
import { useToast } from '../toast.jsx';
import { Logo } from '../components/Layouts.jsx';
import { Field } from '../components/ui.jsx';
import { api } from '../api.js';
import { VERIFY_ROLES } from '../auth.jsx';
import { useFetch } from '../utils.js';

const ROLE_ICON = { donor: '🍛', recipient: '🎒', ngo: '🤝', hospital: '🏥', recycler: '♻️' };

const DEMO = [
  { label: 'Donor', email: 'donor@foodshare.com', password: 'donor123', icon: '🍛' },
  { label: 'NGO', email: 'ngo@foodshare.com', password: 'ngo123', icon: '🤝' },
  { label: 'Admin', email: 'admin@foodshare.com', password: 'admin123', icon: '🛡️' }
];

function strength(pw) {
  let s = 0;
  if (pw.length >= 6) s++;
  if (pw.length >= 10) s++;
  if (/[A-Z]/.test(pw) && /[a-z]/.test(pw)) s++;
  if (/\d/.test(pw) && /[^A-Za-z0-9]/.test(pw)) s++;
  return s;
}

export default function AuthPage({ mode }) {
  const { user, login, signup } = useAuth();
  const toast = useToast();
  const nav = useNavigate();
  const [params] = useSearchParams();
  const signupMode = mode === 'signup';
  const [form, setForm] = useState({ name: '', email: localStorage.getItem('fs_email') || '', password: '', role: ['donor', 'recipient', 'ngo', 'hospital', 'recycler'].includes(params.get('role')) ? params.get('role') : 'donor' });
  const info = useFetch(() => api('/public/modules'));
  const [errors, setErrors] = useState({});
  const [show, setShow] = useState(false);
  const [busy, setBusy] = useState(false);
  const [remember, setRemember] = useState(!!localStorage.getItem('fs_email'));
  const pw = useMemo(() => strength(form.password), [form.password]);
  const set = k => e => { setForm(f => ({ ...f, [k]: e.target.value })); setErrors(x => ({ ...x, [k]: undefined })); };
  const next = params.get('next');

  if (signupMode && params.get('role') === 'ngo') return <Navigate to="/register/ngo" replace />;
  if (user) return <Navigate to={next && next.startsWith('/') ? next : dashboardPath(user.role)} replace />;

  const validate = () => {
    const e = {};
    if (signupMode && form.name.trim().length < 2) e.name = 'Please enter your name.';
    if (!/^[^\s@]+@[^\s@]+\.[^\s@]{2,}$/.test(form.email.trim())) e.email = 'Enter a valid email address.';
    if (!form.password) e.password = 'Password is required.';
    else if (signupMode && form.password.length < 6) e.password = 'Password must be at least 6 characters.';
    setErrors(e);
    return Object.keys(e).length === 0;
  };

  const submit = async ev => {
    ev.preventDefault();
    if (!validate()) return;
    setBusy(true);
    try {
      const email = form.email.trim();
      const u = signupMode ? await signup({ name: form.name.trim(), email, password: form.password, role: form.role }) : await login(email, form.password);
      if (remember) localStorage.setItem('fs_email', email); else localStorage.removeItem('fs_email');
      toast(signupMode ? `Welcome to FoodShare, ${u.name}!` : `Welcome back, ${u.name}!`);
      nav(next && next.startsWith('/') ? next : dashboardPath(u.role), { replace: true });
    } catch (err) {
      setErrors({ ...(err.errors || {}), form: err.message });
      toast(err.message, 'error');
    } finally { setBusy(false); }
  };

  const fill = d => { setForm(f => ({ ...f, email: d.email, password: d.password })); setErrors({}); };

  return (
    <div className="auth">
      <aside className="auth-side">
        <Logo light />
        <div className="auth-scene" aria-hidden="true">
          <div className="orb o1" /><div className="orb o2" />
          <div className="stack3d">
            <div className="card3d c1"><span>🍛</span><b>20 kg</b><small>Veg Biryani · Delivered</small></div>
            <div className="card3d c2"><span>🎁</span><b>+120 pts</b><small>Reward earned</small></div>
            <div className="card3d c3"><span>🌍</span><b>320 kg</b><small>CO₂ reduced</small></div>
          </div>
        </div>
        <blockquote>“Connecting Resources. Empowering Lives.”<small>Food, books, medicines and e-waste — one platform, multiple ways to help.</small></blockquote>
      </aside>

      <main className="auth-main">
        <div className="auth-card">
          <div className="auth-switch" role="tablist">
            <Link role="tab" aria-selected={!signupMode} className={!signupMode ? 'on' : ''} to="/login">Login</Link>
            <Link role="tab" aria-selected={signupMode} className={signupMode ? 'on' : ''} to="/signup">Sign up</Link>
          </div>
          <h1>{signupMode ? 'Create your account' : 'Welcome back'}</h1>
          <p className="muted">{signupMode ? 'Start donating or receiving food in a minute.' : 'Log in to continue making a difference.'}</p>

          {errors.form && <div className="alert bad" role="alert">{errors.form}</div>}

          <form onSubmit={submit} noValidate>
            {signupMode && (
              <div className="role-cards" role="radiogroup" aria-label="I am a">
                {(info.data?.roles || [{ role: 'donor', label: 'Donor', description: 'Donate food, books or e-waste', available: true }, { role: 'ngo', label: 'NGO', description: 'Collect and distribute donations', available: true }]).map(r => (
                  <button type="button" key={r.role} role="radio" aria-checked={form.role === r.role} disabled={!r.available} className={form.role === r.role ? 'on' : ''} onClick={() => (r.role === 'ngo' ? nav('/register/ngo') : setForm(f => ({ ...f, role: r.role })))}>
                    <span>{ROLE_ICON[r.role]}</span><strong>{r.label}</strong><small>{r.role === 'ngo' ? 'Register with details & certificate' : r.available ? r.description : 'Coming soon'}</small>
                  </button>
                ))}
              </div>
            )}
            {signupMode && <Field label={['ngo', 'hospital', 'recycler'].includes(form.role) ? 'Organisation name' : form.role === 'recipient' ? 'Your name' : 'Name / business name'} error={errors.name}><input value={form.name} onChange={set('name')} autoComplete="name" placeholder="e.g. Spice Kitchen" /></Field>}
            <Field label="Email address" error={errors.email}><input type="email" value={form.email} onChange={set('email')} autoComplete="email" placeholder="you@example.com" /></Field>
            <Field label="Password" error={errors.password}>
              <div className="pw">
                <input type={show ? 'text' : 'password'} value={form.password} onChange={set('password')} autoComplete={signupMode ? 'new-password' : 'current-password'} placeholder="At least 6 characters" />
                <button type="button" className="icon-btn" onClick={() => setShow(s => !s)} aria-label={show ? 'Hide password' : 'Show password'}>{show ? '🙈' : '👁️'}</button>
              </div>
            </Field>
            {signupMode && form.password && (
              <div className="meter" aria-label={`Password strength ${pw} of 4`}>
                <span className={`s${pw}`} style={{ width: `${(pw / 4) * 100}%` }} />
                <small>{['Too weak', 'Weak', 'Okay', 'Good', 'Strong'][pw]}</small>
              </div>
            )}
            {!signupMode && (
              <div className="row-between">
                <label className="check"><input type="checkbox" checked={remember} onChange={e => setRemember(e.target.checked)} /> Remember my email</label>
                <button type="button" className="link" onClick={() => toast('Password reset by email is not set up yet — ask an admin.', 'error')}>Forgot password?</button>
              </div>
            )}
            {signupMode && VERIFY_ROLES.includes(form.role) && <div className="alert info">This account type is verified by an admin before all features unlock.</div>}
            <button className="btn btn-primary btn-block btn-lg" disabled={busy}>{busy ? 'Please wait…' : signupMode ? 'Create account' : 'Login'}</button>
          </form>

          {!signupMode && (
            <div className="demo">
              <div className="divider"><span>or try a demo account</span></div>
              <div className="demo-row">{DEMO.map(d => <button key={d.label} type="button" className="btn btn-ghost" onClick={() => fill(d)}>{d.icon} {d.label}</button>)}</div>
            </div>
          )}
          <p className="auth-foot">{signupMode ? <>Already have an account? <Link to="/login">Login</Link></> : <>New here? <Link to="/signup">Create an account</Link></>}</p>
        </div>
      </main>
    </div>
  );
}
