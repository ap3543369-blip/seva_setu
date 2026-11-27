import { useEffect, useMemo, useRef, useState } from 'react';
import { Link, Navigate, useNavigate } from 'react-router-dom';
import { api } from '../api.js';
import { useAuth } from '../auth.jsx';
import { useToast } from '../toast.jsx';
import { ORG_KINDS } from '../constants.js';
import { Field, Spinner } from '../components/ui.jsx';

const EMPTY = { email: '', password: '', confirm: '', orgName: '', orgKind: '', registrationNumber: '', yearEstablished: '', contactPerson: '', contactPhone: '', address: '', city: '', state: '', pincode: '', website: '', focusAreas: '', serviceAreas: '', beneficiariesServed: '', declaration: false };
const FIELD_STEP = { email: 'account', password: 'account', orgName: 'org', orgKind: 'org', registrationNumber: 'org', yearEstablished: 'org', beneficiariesServed: 'org', website: 'org', focusAreas: 'org', serviceAreas: 'org', contactPerson: 'contact', contactPhone: 'contact', address: 'contact', city: 'contact', state: 'contact', pincode: 'contact', declaration: 'docs', certificate: 'docs' };
const TITLES = { account: 'Login details', org: 'About your NGO', contact: 'Contact & address', docs: 'Documents & confirmation' };

/** Dedicated NGO registration. New NGOs create their account here; a rejected NGO comes back to fix and re-send its application. */
export default function NgoRegister() {
  const { user, setUser } = useAuth();
  const toast = useToast();
  const nav = useNavigate();
  const edit = user?.role === 'ngo'; // logged-in NGO that is waiting for / needs changes
  const steps = useMemo(() => (edit ? ['org', 'contact', 'docs'] : ['account', 'org', 'contact', 'docs']), [edit]);
  const [i, setI] = useState(0);
  const [f, setF] = useState(EMPTY);
  const [file, setFile] = useState(null);
  const [hasDoc, setHasDoc] = useState(false);
  const [errors, setErrors] = useState({});
  const [busy, setBusy] = useState(false);
  const [loading, setLoading] = useState(edit);
  const input = useRef(null);
  const year = new Date().getFullYear();
  const set = k => e => { setF(x => ({ ...x, [k]: e.target.type === 'checkbox' ? e.target.checked : e.target.value })); setErrors(x => ({ ...x, [k]: undefined })); };

  useEffect(() => {
    if (!edit) return;
    api('/ngo/application').then(a => {
      setF(x => ({ ...x, orgName: a.orgName, orgKind: a.orgKind, registrationNumber: a.registrationNumber, yearEstablished: a.yearEstablished, contactPerson: a.contactPerson, contactPhone: a.contactPhone, address: a.address, city: a.city, state: a.state, pincode: a.pincode, website: a.website || '', focusAreas: a.focusAreas, serviceAreas: a.serviceAreas || '', beneficiariesServed: a.beneficiariesServed }));
      setHasDoc(a.hasDocument);
    }).catch(() => { /* an older NGO account with no details yet: start with an empty form */ }).finally(() => setLoading(false));
  }, [edit]);

  if (user && user.role !== 'ngo') return <Navigate to="/hub" replace />;
  if (user && user.role === 'ngo' && user.verified) return <Navigate to="/ngo" replace />;

  const key = steps[i];
  const check = step => {
    const e = {};
    if (step === 'account') {
      if (!/^[^\s@]+@[^\s@]+\.[^\s@]{2,}$/.test(f.email.trim())) e.email = 'Enter a valid email address.';
      if (f.password.length < 6) e.password = 'Password must be at least 6 characters.';
      else if (f.password !== f.confirm) e.confirm = 'Passwords do not match.';
    }
    if (step === 'org') {
      if (f.orgName.trim().length < 3) e.orgName = 'Enter the full organisation name.';
      if (!ORG_KINDS.includes(f.orgKind)) e.orgKind = 'Choose the organisation type.';
      if (!/^[A-Za-z0-9/\-_. ]{3,40}$/.test(f.registrationNumber.trim())) e.registrationNumber = 'Enter your registration number (3-40 letters, digits, / - _ .).';
      if (!(+f.yearEstablished >= 1800 && +f.yearEstablished <= year)) e.yearEstablished = `Enter a year between 1800 and ${year}.`;
      if (f.beneficiariesServed === '' || +f.beneficiariesServed < 0 || !Number.isInteger(+f.beneficiariesServed)) e.beneficiariesServed = 'Enter a whole number (0 if you are just starting).';
      if (f.website.trim() && !/^https?:\/\/\S{3,200}$/.test(f.website.trim())) e.website = 'Website must start with http:// or https://';
      if (!f.focusAreas.trim()) e.focusAreas = 'Tell us what your NGO works on.';
    }
    if (step === 'contact') {
      if (!f.contactPerson.trim()) e.contactPerson = 'Enter the contact person’s name.';
      if (!/^[6-9]\d{9}$/.test(f.contactPhone)) e.contactPhone = 'Enter a valid 10-digit mobile number.';
      if (!f.address.trim()) e.address = 'Enter the office address.';
      if (!f.city.trim()) e.city = 'Enter the city.';
      if (!f.state.trim()) e.state = 'Enter the state.';
      if (!/^\d{6}$/.test(f.pincode)) e.pincode = 'Enter a 6-digit PIN code.';
    }
    if (step === 'docs') {
      if (!file && !hasDoc) e.certificate = 'Upload your registration certificate.';
      if (!f.declaration) e.declaration = 'Please confirm to continue.';
    }
    setErrors(x => ({ ...x, ...e }));
    return Object.keys(e).length === 0;
  };
  const next = () => { if (check(key)) setI(n => n + 1); };

  const pick = e => {
    const p = e.target.files[0]; e.target.value = '';
    if (!p) return;
    if (!/\.(pdf|png|jpe?g)$/i.test(p.name)) { setErrors(x => ({ ...x, certificate: 'Only PDF, PNG or JPG files are accepted.' })); return; }
    if (p.size > 5 * 1024 * 1024) { setErrors(x => ({ ...x, certificate: 'The file must be 5 MB or smaller.' })); return; }
    setFile(p); setErrors(x => ({ ...x, certificate: undefined }));
  };

  const submit = async () => {
    const bad = steps.find(s => !check(s));
    if (bad) { setI(steps.indexOf(bad)); return; }
    setBusy(true);
    try {
      const payload = { orgName: f.orgName.trim(), orgKind: f.orgKind, registrationNumber: f.registrationNumber.trim(), yearEstablished: +f.yearEstablished, contactPerson: f.contactPerson.trim(), contactPhone: f.contactPhone, address: f.address.trim(), city: f.city.trim(), state: f.state.trim(), pincode: f.pincode, website: f.website.trim(), focusAreas: f.focusAreas.trim(), serviceAreas: f.serviceAreas.trim(), beneficiariesServed: +f.beneficiariesServed, declaration: f.declaration };
      if (!edit) { payload.email = f.email.trim(); payload.password = f.password; }
      const fd = new FormData();
      fd.append('data', new Blob([JSON.stringify(payload)], { type: 'application/json' }));
      if (file) fd.append('certificate', file);
      await api(edit ? '/ngo/application' : '/ngo/register', { method: edit ? 'PUT' : 'POST', form: fd });
      setUser(await api('/auth/me'));
      toast(edit ? 'Application sent again. The admin will review it.' : 'Application submitted! The admin will verify your NGO.');
      nav('/ngo', { replace: true });
    } catch (ex) {
      setErrors(ex.errors || {});
      const first = Object.keys(ex.errors || {}).map(k => FIELD_STEP[k]).find(s => steps.includes(s));
      if (first) setI(steps.indexOf(first));
      toast(ex.message, 'error');
    } finally { setBusy(false); }
  };

  return (
    <div className="auth">
      <aside className="auth-side reg-side">
        <Link to="/" className="logo light"><img src="/assets/icons/logo.svg" alt="" width="34" height="34" /><span>SevaSetu</span></Link>
        <div className="reg-info">
          <h2>{edit ? 'Update your NGO application' : 'Register your NGO'}</h2>
          <p>We verify every NGO before it can see food, books or medicine donations or send requests. This protects donors and the people you serve.</p>
          <h4>Keep ready</h4>
          <ul><li>Registration number and year of registration</li><li>Registration certificate (PDF, PNG or JPG, up to 5 MB)</li><li>Contact person’s mobile number and office address</li></ul>
          <h4>What happens next</h4>
          <ol><li>You submit this form</li><li>An admin checks your details and certificate</li><li>You get a notification and can start requesting</li></ol>
        </div>
      </aside>
      <main className="auth-main">
        <div className="reg-card">
          {loading ? <Spinner /> : (
            <>
              <ol className="stepper">{steps.map((s, n) => <li key={s} className={n === i ? 'on' : n < i ? 'done' : ''}><span>{n < i ? '✓' : n + 1}</span>{TITLES[s]}</li>)}</ol>
              <h1>{TITLES[key]}</h1>
              {key === 'account' && (
                <>
                  <Field label="Official email *" error={errors.email} hint="You will log in with this email"><input type="email" value={f.email} onChange={set('email')} autoComplete="email" /></Field>
                  <div className="form-grid">
                    <Field label="Password *" error={errors.password}><input type="password" value={f.password} onChange={set('password')} autoComplete="new-password" /></Field>
                    <Field label="Confirm password *" error={errors.confirm}><input type="password" value={f.confirm} onChange={set('confirm')} autoComplete="new-password" /></Field>
                  </div>
                </>
              )}
              {key === 'org' && (
                <>
                  <Field label="Organisation name *" error={errors.orgName}><input value={f.orgName} onChange={set('orgName')} maxLength="120" /></Field>
                  <div className="form-grid">
                    <Field label="Organisation type *" error={errors.orgKind}><select value={f.orgKind} onChange={set('orgKind')}><option value="">Choose…</option>{ORG_KINDS.map(k => <option key={k}>{k}</option>)}</select></Field>
                    <Field label="Registration number *" error={errors.registrationNumber}><input value={f.registrationNumber} onChange={set('registrationNumber')} /></Field>
                    <Field label="Year established *" error={errors.yearEstablished}><input type="number" min="1800" max={year} value={f.yearEstablished} onChange={set('yearEstablished')} /></Field>
                    <Field label="People served so far *" error={errors.beneficiariesServed}><input type="number" min="0" value={f.beneficiariesServed} onChange={set('beneficiariesServed')} /></Field>
                  </div>
                  <Field label="Website (optional)" error={errors.website}><input value={f.website} onChange={set('website')} placeholder="https://" /></Field>
                  <Field label="What does your NGO work on? *" error={errors.focusAreas}><textarea rows="3" maxLength="500" value={f.focusAreas} onChange={set('focusAreas')} placeholder="e.g. meals for daily-wage families, school supplies, health camps" /></Field>
                  <Field label="Areas you serve (optional)" error={errors.serviceAreas}><input value={f.serviceAreas} onChange={set('serviceAreas')} maxLength="300" /></Field>
                </>
              )}
              {key === 'contact' && (
                <>
                  <div className="form-grid">
                    <Field label="Contact person *" error={errors.contactPerson}><input value={f.contactPerson} onChange={set('contactPerson')} /></Field>
                    <Field label="Contact mobile *" error={errors.contactPhone}><input inputMode="numeric" maxLength="10" value={f.contactPhone} onChange={set('contactPhone')} /></Field>
                  </div>
                  <Field label="Office address *" error={errors.address}><textarea rows="2" maxLength="300" value={f.address} onChange={set('address')} /></Field>
                  <div className="form-grid three">
                    <Field label="City *" error={errors.city}><input value={f.city} onChange={set('city')} /></Field>
                    <Field label="State *" error={errors.state}><input value={f.state} onChange={set('state')} /></Field>
                    <Field label="PIN code *" error={errors.pincode}><input inputMode="numeric" maxLength="6" value={f.pincode} onChange={set('pincode')} /></Field>
                  </div>
                </>
              )}
              {key === 'docs' && (
                <>
                  <div className={`field ${errors.certificate ? 'has-error' : ''}`}>
                    <span className="field-label">Registration certificate * (PDF, PNG or JPG, up to 5 MB)</span>
                    <div className="doc-pick">
                      <button type="button" className="btn btn-ghost" onClick={() => input.current.click()}>📎 {file ? 'Change file' : hasDoc ? 'Replace file' : 'Choose file'}</button>
                      <span className="muted small">{file ? file.name : hasDoc ? 'A certificate is already uploaded.' : 'No file chosen'}</span>
                      <input ref={input} type="file" accept=".pdf,.png,.jpg,.jpeg,application/pdf,image/png,image/jpeg" hidden onChange={pick} />
                    </div>
                    {errors.certificate && <small className="field-error">{errors.certificate}</small>}
                    <small className="muted">Only the SevaSetu admin can open this document. It is never shown publicly.</small>
                  </div>
                  <dl className="review">
                    {[['Organisation', `${f.orgName} (${f.orgKind})`], ['Registration', `${f.registrationNumber}, est. ${f.yearEstablished}`], ['Contact', `${f.contactPerson}, ${f.contactPhone}`], ['Address', `${f.address}, ${f.city}, ${f.state} ${f.pincode}`], ['Works on', f.focusAreas]].map(([k, v]) => <div key={k}><dt>{k}</dt><dd>{v}</dd></div>)}
                  </dl>
                  <label className={`check ${errors.declaration ? 'has-error' : ''}`}><input type="checkbox" checked={f.declaration} onChange={set('declaration')} /> I confirm these details are correct and I am authorised to register this NGO.</label>
                  {errors.declaration && <small className="field-error">{errors.declaration}</small>}
                </>
              )}
              <div className="wizard-nav">
                {i > 0 ? <button className="btn btn-ghost" disabled={busy} onClick={() => setI(n => n - 1)}>← Back</button> : <Link className="btn btn-ghost" to={edit ? '/ngo' : '/signup'}>← Cancel</Link>}
                {i < steps.length - 1 ? <button className="btn btn-primary" onClick={next}>Next →</button> : <button className="btn btn-primary" disabled={busy} onClick={submit}>{busy ? 'Submitting…' : edit ? 'Send application again' : 'Submit application'}</button>}
              </div>
              {!edit && <p className="auth-foot">Already registered? <Link to="/login">Login</Link></p>}
            </>
          )}
        </div>
      </main>
    </div>
  );
}
