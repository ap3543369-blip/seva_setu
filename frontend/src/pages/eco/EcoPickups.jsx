import { useMemo, useState } from 'react';
import { api, del, patch, post } from '../../api.js';
import { useAuth } from '../../auth.jsx';
import { useToast } from '../../toast.jsx';
import { fmtDate, useFetch } from '../../utils.js';
import { Async, Empty, Field, Modal, PageHead, StatusBadge, Tabs } from '../../components/ui.jsx';

const TONE = { Requested: 'warn', Scheduled: 'info', Collected: 'ok', Cancelled: 'bad' };
const Badge = ({ s }) => <span className={`badge ${TONE[s] || 'info'}`}>{s}</span>;
const EMPTY = { category: '', description: '', estimatedKg: '', address: '', city: '', pincode: '', phone: '', preferredDate: '' };

function DonorView() {
  const toast = useToast();
  const cats = useFetch(() => api('/ecoai/categories'));
  const list = useFetch(() => api('/ecoai/pickups/mine'));
  const today = useMemo(() => new Date().toISOString().slice(0, 10), []);
  const [f, setF] = useState(EMPTY);
  const [errors, setErrors] = useState({});
  const [busy, setBusy] = useState(false);
  const set = k => e => { setF(x => ({ ...x, [k]: e.target.value })); setErrors(x => ({ ...x, [k]: undefined })); };
  const submit = async e => {
    e.preventDefault();
    const er = {};
    if (!f.category) er.category = 'Choose a category.';
    if (!(+f.estimatedKg >= 0.1)) er.estimatedKg = 'Enter the approximate weight in kg.';
    if (!f.address.trim()) er.address = 'Pickup address is required.';
    if (!f.city.trim()) er.city = 'City is required.';
    if (!/^\d{6}$/.test(f.pincode)) er.pincode = 'Enter a 6-digit PIN code.';
    if (!/^[6-9]\d{9}$/.test(f.phone)) er.phone = 'Enter a valid 10-digit mobile number.';
    if (!f.preferredDate) er.preferredDate = 'Choose a date.'; else if (f.preferredDate < today) er.preferredDate = 'Date cannot be in the past.';
    setErrors(er);
    if (Object.keys(er).length) return;
    setBusy(true);
    try { await post('/ecoai/pickups', { ...f, estimatedKg: +f.estimatedKg }); toast('Pickup requested. Verified recyclers will be notified.'); setF(EMPTY); list.reload(); }
    catch (ex) { setErrors(ex.errors || {}); toast(ex.message, 'error'); } finally { setBusy(false); }
  };
  const cancel = async p => { try { await del(`/ecoai/pickups/${p.id}`); toast('Pickup cancelled.'); list.reload(); } catch (e) { toast(e.message, 'error'); } };
  return (
    <>
      <form className="card" onSubmit={submit} noValidate>
        <h3>Request an e-waste pickup</h3>
        <div className="form-grid">
          <Field label="Category *" error={errors.category}><select value={f.category} onChange={set('category')}><option value="">Choose…</option>{(cats.data || []).map(c => <option key={c}>{c}</option>)}</select></Field>
          <Field label="Approx. weight (kg) *" error={errors.estimatedKg}><input type="number" min="0.1" step="any" value={f.estimatedKg} onChange={set('estimatedKg')} /></Field>
          <Field label="Preferred date *" error={errors.preferredDate}><input type="date" min={today} value={f.preferredDate} onChange={set('preferredDate')} /></Field>
          <Field label="Contact mobile *" error={errors.phone}><input inputMode="numeric" maxLength="10" value={f.phone} onChange={set('phone')} /></Field>
          <Field label="City *" error={errors.city}><input value={f.city} onChange={set('city')} /></Field>
          <Field label="PIN code *" error={errors.pincode}><input inputMode="numeric" maxLength="6" value={f.pincode} onChange={set('pincode')} /></Field>
        </div>
        <Field label="Pickup address *" error={errors.address} hint="Shown only to the recycler who accepts"><textarea rows="2" value={f.address} onChange={set('address')} /></Field>
        <Field label="What are you giving? (optional)" error={errors.description}><textarea rows="2" maxLength="500" value={f.description} onChange={set('description')} /></Field>
        <button className="btn btn-primary" disabled={busy}>{busy ? 'Sending…' : 'Request pickup'}</button>
      </form>
      <h3 className="top-gap">My pickups</h3>
      <Async state={list}>{rows => rows.length === 0 ? <Empty icon="♻️" title="No pickups yet" /> : (
        <div className="card table-wrap"><table className="table">
          <thead><tr><th>Category</th><th>Est. kg</th><th>Preferred</th><th>Recycler</th><th>Status</th><th /></tr></thead>
          <tbody>{rows.map(p => (
            <tr key={p.id}><td data-label="Category">{p.category}</td><td data-label="Est. kg">{p.estimatedKg}{p.actualKg ? ` (collected ${p.actualKg})` : ''}</td><td data-label="Preferred">{fmtDate(p.preferredDate)}{p.scheduledDate ? ` → ${fmtDate(p.scheduledDate)}` : ''}</td>
              <td data-label="Recycler">{p.recycler || '—'}</td><td data-label="Status"><Badge s={p.status} /></td>
              <td className="actions">{(p.status === 'Requested' || p.status === 'Scheduled') && <button className="btn btn-sm btn-danger" onClick={() => cancel(p)}>Cancel</button>}</td></tr>
          ))}</tbody></table></div>
      )}</Async>
    </>
  );
}

function RecyclerView() {
  const toast = useToast();
  const [tab, setTab] = useState('open');
  const open = useFetch(() => api('/ecoai/pickups/open'));
  const mine = useFetch(() => api('/ecoai/pickups/assigned'));
  const [collect, setCollect] = useState(null);
  const [kg, setKg] = useState('');
  const refresh = () => { open.reload(); mine.reload(); };
  const act = async (p, body, msg) => { try { await patch(`/ecoai/pickups/${p.id}`, body); toast(msg); setCollect(null); setKg(''); refresh(); } catch (e) { toast(e.message, 'error'); } };
  const list = tab === 'open' ? open : mine;
  return (
    <>
      <Tabs value={tab} onChange={setTab} tabs={[{ key: 'open', label: 'Open requests', count: open.data?.length }, { key: 'mine', label: 'My pickups', count: mine.data?.length }]} />
      <Async state={list}>{rows => rows.length === 0 ? <Empty icon="🚚" title={tab === 'open' ? 'No open requests' : 'No pickups assigned to you'} /> : (
        <div className="grid-3">
          {rows.map(p => (
            <article className="card med-card" key={p.id}>
              <div className="row-between"><span className="chip">{p.category}</span><Badge s={p.status} /></div>
              <h3>~{p.estimatedKg} kg {p.actualKg ? <small className="muted">(collected {p.actualKg})</small> : null}</h3>
              <p className="muted small">{p.donor} · {p.city}<br />Preferred {fmtDate(p.preferredDate)}{p.scheduledDate ? ` · scheduled ${fmtDate(p.scheduledDate)}` : ''}</p>
              {p.description && <p className="note">“{p.description}”</p>}
              {p.address && <p className="small">📍 {p.address}<br />📞 {p.phone}</p>}
              {p.status === 'Requested' && <button className="btn btn-primary btn-block" onClick={() => act(p, { status: 'SCHEDULED' }, 'Pickup accepted and scheduled.')}>Accept pickup</button>}
              {p.status === 'Scheduled' && <div className="row-gap"><button className="btn btn-primary" onClick={() => { setCollect(p); setKg(''); }}>Mark collected</button><button className="btn btn-ghost" onClick={() => act(p, { status: 'REQUESTED' }, 'Pickup released.')}>Release</button></div>}
            </article>
          ))}
        </div>
      )}</Async>
      {collect && (
        <Modal title="Mark as collected" onClose={() => setCollect(null)} footer={<><button className="btn btn-ghost" onClick={() => setCollect(null)}>Cancel</button><button className="btn btn-primary" disabled={!(+kg > 0)} onClick={() => act(collect, { status: 'COLLECTED', actualKg: +kg }, 'Collected and added to your records.')}>Save</button></>}>
          <Field label="Actual weight collected (kg)"><input type="number" min="0.1" step="any" value={kg} onChange={e => setKg(e.target.value)} autoFocus /></Field>
          <p className="muted small">This weight is added to this month’s records, which feed the forecast.</p>
        </Modal>
      )}
    </>
  );
}

export default function EcoPickups() {
  const { user } = useAuth();
  return (
    <>
      <PageHead title={user.role === 'recycler' ? 'Pickups' : 'E-waste pickup'} subtitle={user.role === 'recycler' ? 'Accept requests and record what you collect.' : 'Give old electronics to a verified recycler.'} />
      {user.role === 'recycler' ? <RecyclerView /> : <DonorView />}
    </>
  );
}
