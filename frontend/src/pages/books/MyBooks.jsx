import { useState } from 'react';
import { Link } from 'react-router-dom';
import { api, del, patch } from '../../api.js';
import { useToast } from '../../toast.jsx';
import { fmtDate, useFetch } from '../../utils.js';
import { Async, Empty, PageHead, StatusBadge } from '../../components/ui.jsx';

function Requests({ id, onChange }) {
  const toast = useToast();
  const state = useFetch(() => api(`/books/${id}/requests`), [id]);
  const act = async (r, status) => {
    try { await patch(`/books/requests/${r.id}/status`, { status }); toast(`Request ${status.toLowerCase()}.`); state.reload(); onChange(); }
    catch (e) { toast(e.message, 'error'); }
  };
  return (
    <Async state={state}>{rows => rows.length === 0 ? <p className="muted small">No requests yet.</p> : (
      <ul className="rows">
        {rows.map(r => (
          <li key={r.id}>
            <div>
              <strong>{r.counterpart}</strong> <small className="muted">({r.counterpartType}) · {r.quantity} cop{r.quantity === 1 ? 'y' : 'ies'} · {fmtDate(r.createdAt)}</small>
              {r.note && <p className="note">“{r.note}”</p>}
              {r.phone && <p className="small">📞 {r.phone}</p>}
            </div>
            <div className="row-gap">
              <StatusBadge status={r.status} />
              {r.status === 'Pending' && <><button className="btn btn-sm btn-primary" onClick={() => act(r, 'ACCEPTED')}>Accept</button><button className="btn btn-sm btn-ghost" onClick={() => act(r, 'DECLINED')}>Decline</button></>}
              {r.status === 'Accepted' && <><button className="btn btn-sm btn-primary" onClick={() => act(r, 'COMPLETED')}>Mark handed over</button><button className="btn btn-sm btn-ghost" onClick={() => act(r, 'DECLINED')}>Cancel</button></>}
            </div>
          </li>
        ))}
      </ul>
    )}</Async>
  );
}

export default function MyBooks() {
  const toast = useToast();
  const state = useFetch(() => api('/books/mine'));
  const [open, setOpen] = useState(null);
  const cancel = async b => {
    if (!window.confirm(`Withdraw “${b.title}”? Open requests will be declined.`)) return;
    try { await del(`/books/${b.id}`); toast('Listing withdrawn.'); state.reload(); } catch (e) { toast(e.message, 'error'); }
  };
  return (
    <>
      <PageHead title="My books" subtitle="Manage your listings and the requests you receive.">
        <Link to="/books/donate" className="btn btn-primary">➕ Donate books</Link>
      </PageHead>
      <Async state={state}>{rows => rows.length === 0 ? <Empty icon="📚" title="You have not listed any books yet"><Link to="/books/donate">List your first books</Link></Empty> : (
        <div className="stack">
          {rows.map(b => (
            <div className="card" key={b.id}>
              <div className="row-between">
                <div>
                  <h3><Link to={`/books/${b.id}`}>{b.title}</Link> <small className="muted">· {b.subject} · {b.level}</small></h3>
                  <p className="muted small">{b.quantityLeft} of {b.quantity} copies left · listed {fmtDate(b.createdAt)} · {b.pendingRequests || 0} waiting</p>
                  {b.status === 'Awaiting approval' && <p className="note">Waiting for the admin to approve. NGOs and students cannot see it yet.</p>}
                  {b.status === 'Rejected' && b.reviewNote && <p className="note">Admin: {b.reviewNote}</p>}
                </div>
                <div className="row-gap">
                  <StatusBadge status={b.status} />
                  <button className="btn btn-sm btn-ghost" onClick={() => setOpen(open === b.id ? null : b.id)}>{open === b.id ? 'Hide requests' : 'Requests'}</button>
                  {b.status !== 'Donated' && b.status !== 'Cancelled' && b.status !== 'Rejected' && <button className="btn btn-sm btn-danger" onClick={() => cancel(b)}>Withdraw</button>}
                </div>
              </div>
              {open === b.id && <Requests id={b.id} onChange={state.reload} />}
            </div>
          ))}
        </div>
      )}</Async>
    </>
  );
}
