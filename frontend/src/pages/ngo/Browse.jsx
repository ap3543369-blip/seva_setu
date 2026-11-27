import { useEffect, useState } from 'react';
import { api, post } from '../../api.js';
import { useAuth } from '../../auth.jsx';
import { useToast } from '../../toast.jsx';
import { fmtPickup, useFetch } from '../../utils.js';
import { Async, Empty, Modal, PageHead } from '../../components/ui.jsx';
import FoodCard from '../../components/FoodCard.jsx';

const CATS = ['All', 'Cooked', 'Packed', 'Bakery', 'Vegetables', 'Raw'];

export default function Browse() {
  const { user } = useAuth();
  const toast = useToast();
  const [q, setQ] = useState('');
  const [dq, setDq] = useState('');
  const [cat, setCat] = useState('All');
  const [dist, setDist] = useState('');
  const [target, setTarget] = useState(null);
  const [note, setNote] = useState('');
  const [busy, setBusy] = useState(false);
  useEffect(() => { const id = setTimeout(() => setDq(q), 300); return () => clearTimeout(id); }, [q]);
  const state = useFetch(() => {
    const p = new URLSearchParams();
    if (dq.trim()) p.set('q', dq.trim());
    if (cat !== 'All') p.set('category', cat);
    if (dist) p.set('maxDistance', dist);
    return api(`/donations/available?${p}`);
  }, [dq, cat, dist]);

  const send = async () => {
    setBusy(true);
    try { await post('/requests', { donationId: target.id, note: note.trim() }); toast(`Request sent to ${target.donor}!`); setTarget(null); setNote(''); state.reload(); }
    catch (e) { toast(e.message, 'error'); } finally { setBusy(false); }
  };

  return (
    <>
      <PageHead title="Find food" subtitle="Browse available donations near you and send a request." />
      {!user.verified && <div className="alert warn">⏳ Requests unlock after an admin verifies your NGO.</div>}
      <div className="filters card">
        <input className="search" placeholder="🔎  Search by food, donor or area…" value={q} onChange={e => setQ(e.target.value)} aria-label="Search" />
        <div className="chips">{CATS.map(c => <button key={c} className={`chip-btn ${cat === c ? 'on' : ''}`} onClick={() => setCat(c)}>{c}</button>)}</div>
        <select value={dist} onChange={e => setDist(e.target.value)} aria-label="Maximum distance"><option value="">Any distance</option><option value="2">Within 2 km</option><option value="5">Within 5 km</option><option value="10">Within 10 km</option></select>
      </div>
      <Async state={state}>{rows => rows.length === 0 ? <Empty icon="🔎" title="No donations match">Try removing a filter or check again soon.</Empty> : (
        <>
          <p className="muted">{rows.length} donation{rows.length > 1 ? 's' : ''} found</p>
          <div className="grid-3">
            {rows.map(d => <FoodCard key={d.id} d={d} action={d.requested ? <button className="btn btn-ghost btn-block" disabled>Requested ✓</button> : <button className="btn btn-primary btn-block" onClick={() => setTarget(d)}>Request</button>} />)}
          </div>
        </>
      )}</Async>
      {target && (
        <Modal title={`Request ${target.food}`} onClose={() => setTarget(null)} footer={<><button className="btn btn-ghost" onClick={() => setTarget(null)}>Cancel</button><button className="btn btn-primary" disabled={busy} onClick={send}>{busy ? 'Sending…' : 'Send request'}</button></>}>
          <p>Request <strong>{target.qty}</strong> from <strong>{target.donor}</strong>? Pickup is available {fmtPickup(target.pickupAt)}.</p>
          <textarea rows="3" maxLength="500" placeholder="Add a note for the donor (optional)" value={note} onChange={e => setNote(e.target.value)} />
        </Modal>
      )}
    </>
  );
}
