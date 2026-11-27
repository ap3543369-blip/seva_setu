import { useState } from 'react';
import { post } from '../api.js';
import { useToast } from '../toast.jsx';
import { Field } from '../components/ui.jsx';

export default function Contact() {
  const toast = useToast();
  const [f, setF] = useState({ name: '', email: '', subject: '', message: '' });
  const [errors, setErrors] = useState({});
  const [busy, setBusy] = useState(false);
  const [sent, setSent] = useState(false);
  const set = k => e => { setF(x => ({ ...x, [k]: e.target.value })); setErrors(x => ({ ...x, [k]: undefined })); };

  const submit = async e => {
    e.preventDefault();
    const err = {};
    if (!f.name.trim()) err.name = 'Please enter your name.';
    if (!/^[^\s@]+@[^\s@]+\.[^\s@]{2,}$/.test(f.email.trim())) err.email = 'Enter a valid email address.';
    if (!f.subject.trim()) err.subject = 'Please add a subject.';
    if (f.message.trim().length < 10) err.message = 'Message must be at least 10 characters.';
    setErrors(err);
    if (Object.keys(err).length) return;
    setBusy(true);
    try { await post('/contact', f); setSent(true); toast('Message sent. Thank you!'); }
    catch (ex) { setErrors(ex.errors || {}); toast(ex.message, 'error'); }
    finally { setBusy(false); }
  };

  return (
    <div className="container page-pad">
      <div className="section-head"><span className="eyebrow">Contact</span><h1>We’d love to hear from you</h1><p>Questions, partnerships or feedback — send us a note.</p></div>
      <div className="two-col contact">
        <div className="card info-card">
          <h3>Get in touch</h3>
          <p>📍 Delhi, India</p><p>✉️ hello@foodshare.example</p><p>🕘 Mon–Sat, 9am – 6pm</p>
          <div className="mini-map" aria-hidden="true"><i /><i /><i /></div>
        </div>
        <div className="card">
          {sent ? (
            <div className="empty"><div className="empty-icon">✅</div><strong>Message received</strong><p>Our team will get back to you soon.</p><button className="btn btn-ghost" onClick={() => { setSent(false); setF({ name: '', email: '', subject: '', message: '' }); }}>Send another</button></div>
          ) : (
            <form onSubmit={submit} noValidate>
              <div className="form-grid">
                <Field label="Name" error={errors.name}><input value={f.name} onChange={set('name')} /></Field>
                <Field label="Email" error={errors.email}><input type="email" value={f.email} onChange={set('email')} /></Field>
              </div>
              <Field label="Subject" error={errors.subject}><input value={f.subject} onChange={set('subject')} /></Field>
              <Field label="Message" error={errors.message}><textarea rows="5" value={f.message} onChange={set('message')} /></Field>
              <button className="btn btn-primary" disabled={busy}>{busy ? 'Sending…' : 'Send message'}</button>
            </form>
          )}
        </div>
      </div>
    </div>
  );
}
