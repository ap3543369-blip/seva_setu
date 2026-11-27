import { useState } from 'react';
import { useNavigate, useParams } from 'react-router-dom';
import { api, post } from '../../api.js';
import { useAuth } from '../../auth.jsx';
import { useToast } from '../../toast.jsx';
import { fmtDate, useFetch } from '../../utils.js';
import { Async, Field, StatusBadge } from '../../components/ui.jsx';

export default function BookDetail() {
  const { id } = useParams();
  const { user } = useAuth();
  const toast = useToast();
  const nav = useNavigate();
  const state = useFetch(() => api(`/books/${id}`), [id]);
  const [img, setImg] = useState(0);
  const [form, setForm] = useState({ quantity: 1, note: '' });
  const [err, setErr] = useState({});
  const [busy, setBusy] = useState(false);
  const canRequest = user.role === 'recipient' || user.role === 'ngo';

  const send = async b => {
    setBusy(true);
    try { await post('/books/requests', { listingId: b.id, quantity: +form.quantity, note: form.note.trim() }); toast('Request sent!'); state.reload(); }
    catch (e) { setErr(e.errors || {}); toast(e.message, 'error'); } finally { setBusy(false); }
  };

  return (
    <>
      <button className="link back" onClick={() => nav(-1)}>← Back</button>
      <Async state={state}>{b => (
        <div className="detail">
          <div>
            <div className="gallery">{b.images?.length ? <img src={b.images[Math.min(img, b.images.length - 1)]} alt={b.title} /> : <div className="book-emoji big">📚</div>}</div>
            {b.images?.length > 1 && <div className="thumbs">{b.images.map((p, i) => <button key={p} className={i === img ? 'on' : ''} onClick={() => setImg(i)}><img src={p} alt={`Photo ${i + 1}`} /></button>)}</div>}
          </div>
          <div className="card">
            <div className="row-between"><span className="chip">{b.condition}</span><StatusBadge status={b.status} /></div>
            <h1>{b.title}</h1>
            <p className="muted">{b.author || 'Unknown author'} · by donor <strong>{b.donor}</strong> · listed {fmtDate(b.createdAt)}</p>
            {b.description && <p>{b.description}</p>}
            <dl className="facts">
              <div><dt>Subject</dt><dd>{b.subject}</dd></div><div><dt>Class / level</dt><dd>{b.level}</dd></div>
              <div><dt>Language</dt><dd>{b.language || '—'}</dd></div><div><dt>Copies left</dt><dd>{b.quantityLeft} of {b.quantity}</dd></div>
              <div><dt>City</dt><dd>{b.city}</dd></div>
              <div><dt>Pickup address</dt><dd>{b.address || <em className="muted">Shared after the donor accepts your request</em>}</dd></div>
              <div><dt>Phone</dt><dd>{b.phone || <em className="muted">Shared after acceptance</em>}</dd></div>
            </dl>
            {canRequest && b.status === 'Available' && !b.myRequestStatus && (
              <div className="request-box">
                <Field label={`Copies needed (max ${b.quantityLeft})`} error={err.quantity}><input type="number" min="1" max={b.quantityLeft} value={form.quantity} onChange={e => setForm(x => ({ ...x, quantity: e.target.value }))} /></Field>
                <Field label="Note to the donor" error={err.note}><textarea rows="2" maxLength="500" value={form.note} onChange={e => setForm(x => ({ ...x, note: e.target.value }))} /></Field>
                <button className="btn btn-primary" disabled={busy} onClick={() => send(b)}>{busy ? 'Sending…' : 'Request these books'}</button>
              </div>
            )}
            {b.myRequestStatus && <div className="alert info">Your request: {b.myRequestStatus}</div>}
          </div>
        </div>
      )}</Async>
    </>
  );
}
