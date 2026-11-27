import { useEffect, useState } from 'react';
import { api, post } from '../../api.js';
import { useToast } from '../../toast.jsx';
import { fmtDate, useFetch } from '../../utils.js';
import { Async, Empty, Field, Modal, PageHead } from '../../components/ui.jsx';
import { ReqBadge } from './MediMine.jsx';

export default function MediBrowse() {
  const toast = useToast();
  const policy = useFetch(() => api('/medishare/policy'));
  const [q, setQ] = useState('');
  const [dq, setDq] = useState('');
  const [cat, setCat] = useState('');
  const [ask, setAsk] = useState(null);
  const [form, setForm] = useState({ quantity: 1, purpose: '', registrationNumber: '' });
  const [err, setErr] = useState({});
  const [busy, setBusy] = useState(false);
  useEffect(() => { const id = setTimeout(() => setDq(q), 300); return () => clearTimeout(id); }, [q]);
  const state = useFetch(() => { const p = new URLSearchParams(); if (dq.trim()) p.set('q', dq.trim()); if (cat) p.set('category', cat); return api(`/medishare/donations/available?${p}`); }, [dq, cat]);

  const send = async () => {
    const e = {};
    if (!(+form.quantity >= 1)) e.quantity = 'Enter at least 1.';
    if (!form.purpose.trim()) e.purpose = 'Say how the items will be used.';
    if (form.registrationNumber.trim().length < 3) e.registrationNumber = 'Your registration / licence number is required.';
    setErr(e);
    if (Object.keys(e).length) return;
    setBusy(true);
    try { await post('/medishare/requests', { donationId: ask.id, quantity: +form.quantity, purpose: form.purpose.trim(), registrationNumber: form.registrationNumber.trim() }); toast('Request sent.'); setAsk(null); state.reload(); }
    catch (ex) { setErr(ex.errors || {}); toast(ex.message, 'error'); } finally { setBusy(false); }
  };

  return (
    <>
      <PageHead title="Find medicines & supplies" subtitle="Available items from authorised hospitals and pharmacies." />
      <div className="filters card">
        <input className="search" placeholder="🔎  Search by name…" value={q} onChange={e => setQ(e.target.value)} aria-label="Search" />
        <select value={cat} onChange={e => setCat(e.target.value)} aria-label="Category"><option value="">All categories</option>{(policy.data?.allowedCategories || []).map(c => <option key={c}>{c}</option>)}</select>
        <span />
      </div>
      <Async state={state}>{rows => rows.length === 0 ? <Empty icon="💊" title="Nothing available right now">Check again soon.</Empty> : (
        <div className="grid-3">
          {rows.map(d => (
            <article className="card med-card" key={d.id}>
              <span className="chip">{d.category}</span>
              <h3>{d.medicineName} {d.strength && <small className="muted">{d.strength}</small>}</h3>
              <p className="muted small">{d.donor} · {d.city}</p>
              <div className="food-meta"><span>📦 {d.quantityLeft} {d.unit}</span><span>⏳ expires {fmtDate(d.expiryDate)}</span><span>🌡️ {d.storage}</span></div>
              {d.myRequestStatus ? <div><ReqBadge s={d.myRequestStatus} /></div> : <button className="btn btn-primary btn-block" onClick={() => { setAsk(d); setErr({}); setForm({ quantity: 1, purpose: '', registrationNumber: '' }); }}>Request</button>}
            </article>
          ))}
        </div>
      )}</Async>
      {ask && (
        <Modal title={`Request ${ask.medicineName}`} onClose={() => setAsk(null)} footer={<><button className="btn btn-ghost" onClick={() => setAsk(null)}>Cancel</button><button className="btn btn-primary" disabled={busy} onClick={send}>{busy ? 'Sending…' : 'Send request'}</button></>}>
          <Field label={`Quantity (max ${ask.quantityLeft} ${ask.unit})`} error={err.quantity}><input type="number" min="1" max={ask.quantityLeft} value={form.quantity} onChange={e => setForm(x => ({ ...x, quantity: e.target.value }))} /></Field>
          <Field label="How will the items be used? *" error={err.purpose}><textarea rows="3" maxLength="500" value={form.purpose} onChange={e => setForm(x => ({ ...x, purpose: e.target.value }))} /></Field>
          <Field label="Your NGO / hospital registration no. *" error={err.registrationNumber}><input value={form.registrationNumber} onChange={e => setForm(x => ({ ...x, registrationNumber: e.target.value }))} /></Field>
        </Modal>
      )}
    </>
  );
}
