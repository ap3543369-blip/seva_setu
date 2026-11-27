import { useState } from 'react';
import { Link, useNavigate, useParams } from 'react-router-dom';
import { api, post, del } from '../api.js';
import { useAuth } from '../auth.jsx';
import { useToast } from '../toast.jsx';
import { fmtDateTime, fmtPickup, foodImage, useFetch } from '../utils.js';
import { Async, Modal, StatusBadge } from '../components/ui.jsx';

const FLOW = ['Pending', 'Accepted', 'Delivered'];

export default function DonationDetail() {
  const { id } = useParams();
  const { user } = useAuth();
  const toast = useToast();
  const nav = useNavigate();
  const state = useFetch(() => api(`/donations/${id}`), [id]);
  const [img, setImg] = useState(0);
  const [ask, setAsk] = useState(false);
  const [note, setNote] = useState('');
  const [busy, setBusy] = useState(false);

  const request = async d => {
    setBusy(true);
    try { await post('/requests', { donationId: d.id, note: note.trim() }); toast(`Request sent to ${d.donor}!`); setAsk(false); state.reload(); }
    catch (e) { toast(e.message, 'error'); } finally { setBusy(false); }
  };
  const cancel = async d => {
    if (!window.confirm('Cancel this donation? Points earned for it will be taken back.')) return;
    try { await del(`/donations/${d.id}`); toast('Donation cancelled.'); state.reload(); } catch (e) { toast(e.message, 'error'); }
  };

  return (
    <div className="container page-pad">
      <button className="link back" onClick={() => nav(-1)}>← Back</button>
      <Async state={state}>{d => {
        const photos = d.images?.length ? d.images : [foodImage(d)];
        const step = FLOW.indexOf(d.status);
        const mine = user?.role === 'donor' && user.name === d.donor;
        return (
          <div className="detail">
            <div>
              <div className="gallery"><img src={photos[Math.min(img, photos.length - 1)]} alt={d.food} /></div>
              {photos.length > 1 && <div className="thumbs">{photos.map((p, i) => <button key={p} className={i === img ? 'on' : ''} onClick={() => setImg(i)}><img src={p} alt={`Photo ${i + 1}`} /></button>)}</div>}
            </div>
            <div className="card">
              <div className="row-between"><span className="chip">{d.category}</span><StatusBadge status={d.status} /></div>
              <h1>{d.food} <small>({d.qty})</small></h1>
              <p className="muted">By <strong>{d.donor}</strong> · {d.area} · 📍 {d.distanceKm} km away</p>
              {d.status === 'Awaiting approval' && <div className="alert warn"><span>This donation is waiting for admin approval and is not visible to NGOs yet.</span></div>}
              {d.status === 'Rejected' && <div className="alert bad"><span>Rejected by the admin{d.reviewNote ? `: ${d.reviewNote}` : '.'}</span></div>}
              {d.description && <p>{d.description}</p>}
              <dl className="facts">
                <div><dt>Meals</dt><dd>🍛 {d.meals}</dd></div>
                <div><dt>Condition</dt><dd>{d.condition}</dd></div>
                <div><dt>Pickup</dt><dd>{fmtPickup(d.pickupAt)}</dd></div>
                <div><dt>Best before</dt><dd>{fmtDateTime(d.bestBefore)}</dd></div>
                <div><dt>Handover</dt><dd>{d.handover || '—'}</dd></div>
                <div><dt>Reward</dt><dd>⭐ {d.points} pts</dd></div>
                <div><dt>Address</dt><dd>{d.address || <em className="muted">Shared after the donor accepts your request</em>}</dd></div>
                <div><dt>Phone</dt><dd>{d.phone || <em className="muted">Shared after acceptance</em>}</dd></div>
              </dl>
              {d.status === 'Cancelled' ? <div className="alert bad">This donation was cancelled.</div> : (
                <ol className="timeline">
                  {FLOW.map((s, i) => <li key={s} className={i <= step ? 'done' : ''}><span>{i < step ? '✓' : i + 1}</span>{s}{i === 1 && d.ngo ? <small> · {d.ngo}</small> : null}</li>)}
                </ol>
              )}
              <div className="detail-actions">
                {user?.role === 'ngo' && d.status === 'Pending' && (d.requested ? <span className="badge info">Request sent</span> : <button className="btn btn-primary" onClick={() => setAsk(true)}>Request this food</button>)}
                {mine && (d.status === 'Pending' || d.status === 'Awaiting approval') && <button className="btn btn-danger" onClick={() => cancel(d)}>Cancel donation</button>}
                {!user && <Link className="btn btn-primary" to={`/login?next=${encodeURIComponent(`/donation/${d.id}`)}`}>Login to request</Link>}
              </div>
            </div>
            {ask && (
              <Modal title={`Request ${d.food}`} onClose={() => setAsk(false)} footer={<><button className="btn btn-ghost" onClick={() => setAsk(false)}>Cancel</button><button className="btn btn-primary" disabled={busy} onClick={() => request(d)}>{busy ? 'Sending…' : 'Send request'}</button></>}>
                <p>Send a request to <strong>{d.donor}</strong>. Pickup is available {fmtPickup(d.pickupAt)}.</p>
                <textarea rows="3" maxLength="500" placeholder="Add a note (optional) – e.g. our van arrives at 3 PM" value={note} onChange={e => setNote(e.target.value)} />
              </Modal>
            )}
          </div>
        );
      }}</Async>
    </div>
  );
}
