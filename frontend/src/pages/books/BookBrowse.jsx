import { useEffect, useState } from 'react';
import { Link } from 'react-router-dom';
import { api, post } from '../../api.js';
import { useAuth } from '../../auth.jsx';
import { useToast } from '../../toast.jsx';
import { LEVELS, SUBJECTS } from '../../constants.js';
import { fmt, useFetch } from '../../utils.js';
import { Async, Empty, Field, Modal, PageHead, Stat, StatusBadge } from '../../components/ui.jsx';

export function BookCard({ b, action }) {
  return (
    <article className="food-card">
      <Link to={`/books/${b.id}`} className="food-img book-img">
        {b.images?.length ? <img src={b.images[0]} alt={b.title} loading="lazy" /> : <span className="book-emoji" aria-hidden="true">📚</span>}
        <span className="chip">{b.condition}</span>
      </Link>
      <div className="food-body">
        <h3><Link to={`/books/${b.id}`}>{b.title}</Link></h3>
        <p className="muted">{b.author || 'Unknown author'} · {b.subject} · {b.level}</p>
        <div className="food-meta"><span>📍 {b.city}</span><span>📦 {b.quantityLeft} of {b.quantity} left</span><span>🙋 {b.donor}</span></div>
        {b.myRequestStatus && <div><StatusBadge status={b.myRequestStatus} /></div>}
        {action && <div className="food-actions">{action}</div>}
      </div>
    </article>
  );
}

export default function BookBrowse() {
  const { user } = useAuth();
  const toast = useToast();
  const canRequest = user.role === 'recipient' || user.role === 'ngo';
  const [f, setF] = useState({ q: '', subject: '', level: '', city: '' });
  const [dq, setDq] = useState(f);
  const [ask, setAsk] = useState(null);
  const [form, setForm] = useState({ quantity: 1, note: '' });
  const [err, setErr] = useState({});
  const [busy, setBusy] = useState(false);
  useEffect(() => { const id = setTimeout(() => setDq(f), 300); return () => clearTimeout(id); }, [f]);
  const state = useFetch(() => {
    const p = new URLSearchParams();
    Object.entries(dq).forEach(([k, v]) => v.trim() && p.set(k, v.trim()));
    return api(`/books/available?${p}`);
  }, [dq]);
  const stats = useFetch(() => api('/books/stats'));
  const set = k => e => setF(x => ({ ...x, [k]: e.target.value }));

  const send = async () => {
    setBusy(true);
    try {
      await post('/books/requests', { listingId: ask.id, quantity: +form.quantity, note: form.note.trim() });
      toast(`Request sent to ${ask.donor}!`); setAsk(null); setForm({ quantity: 1, note: '' }); state.reload(); stats.reload();
    } catch (e) { setErr(e.errors || {}); toast(e.message, 'error'); } finally { setBusy(false); }
  };

  return (
    <>
      <PageHead title="BookShare" subtitle="Find books and study material shared by donors.">
        {user.role === 'donor' && <Link to="/books/donate" className="btn btn-primary">➕ Donate books</Link>}
      </PageHead>
      {stats.data && (canRequest ? (
        <div className="stat-grid four"><Stat icon="📋" label="My requests" value={fmt(stats.data.myRequests)} /><Stat icon="✅" label="Accepted" value={fmt(stats.data.myAccepted)} tone="orange" /><Stat icon="📖" label="Books received" value={fmt(stats.data.copiesReceived)} tone="blue" /></div>
      ) : user.role === 'donor' ? (
        <div className="stat-grid four"><Stat icon="📚" label="My listings" value={fmt(stats.data.listings)} /><Stat icon="🎁" label="Copies given" value={fmt(stats.data.copiesGiven)} tone="orange" /><Stat icon="⏳" label="Requests waiting" value={fmt(stats.data.pendingRequests)} tone="purple" /></div>
      ) : null)}
      <div className="filters card book-filters">
        <input className="search" placeholder="🔎  Search title, author or subject…" value={f.q} onChange={set('q')} aria-label="Search" />
        <select value={f.subject} onChange={set('subject')} aria-label="Subject"><option value="">All subjects</option>{SUBJECTS.map(s => <option key={s}>{s}</option>)}</select>
        <select value={f.level} onChange={set('level')} aria-label="Class or level"><option value="">All levels</option>{LEVELS.map(s => <option key={s}>{s}</option>)}</select>
        <input placeholder="City" value={f.city} onChange={set('city')} aria-label="City" />
      </div>
      <Async state={state}>{rows => rows.length === 0 ? <Empty icon="📚" title="No books match">Try different filters, or check back soon.</Empty> : (
        <div className="grid-3">
          {rows.map(b => <BookCard key={b.id} b={b} action={canRequest ? (b.myRequestStatus ? <button className="btn btn-ghost btn-block" disabled>Requested</button> : <button className="btn btn-primary btn-block" onClick={() => { setAsk(b); setErr({}); }}>Request</button>) : null} />)}
        </div>
      )}</Async>
      {ask && (
        <Modal title={`Request “${ask.title}”`} onClose={() => setAsk(null)} footer={<><button className="btn btn-ghost" onClick={() => setAsk(null)}>Cancel</button><button className="btn btn-primary" disabled={busy} onClick={send}>{busy ? 'Sending…' : 'Send request'}</button></>}>
          <Field label={`Copies needed (max ${ask.quantityLeft})`} error={err.quantity}><input type="number" min="1" max={ask.quantityLeft} value={form.quantity} onChange={e => setForm(x => ({ ...x, quantity: e.target.value }))} /></Field>
          <Field label="Note to the donor (optional)" error={err.note}><textarea rows="3" maxLength="500" value={form.note} onChange={e => setForm(x => ({ ...x, note: e.target.value }))} placeholder="Tell them who the books are for" /></Field>
        </Modal>
      )}
    </>
  );
}
