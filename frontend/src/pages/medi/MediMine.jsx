import { useState } from 'react';
import { Link } from 'react-router-dom';
import { api, del, patch } from '../../api.js';
import { useToast } from '../../toast.jsx';
import { fmtDate, useFetch } from '../../utils.js';
import { Async, Empty, Modal, PageHead } from '../../components/ui.jsx';

const TONE = { LISTED: 'ok', PENDING_REVIEW: 'warn', REJECTED: 'bad', SUSPENDED: 'bad', WITHDRAWN: 'info', FULLY_ALLOCATED: 'info' };
export const MediBadge = ({ d }) => <span className={`badge ${TONE[d.status] || 'info'}`}>{d.statusLabel}</span>;
const REQ_TONE = { Pending: 'warn', Approved: 'info', Collected: 'ok', Rejected: 'bad', Cancelled: 'bad' };
export const ReqBadge = ({ s }) => <span className={`badge ${REQ_TONE[s] || 'info'}`}>{s}</span>;

function Incoming({ id, onChange }) {
  const toast = useToast();
  const state = useFetch(() => api(`/medishare/donations/${id}/requests`), [id]);
  const [reject, setReject] = useState(null);
  const [note, setNote] = useState('');
  const act = async (r, status, n) => {
    try { await patch(`/medishare/requests/${r.id}/status`, { status, note: n || undefined }); toast(`Request ${status.toLowerCase()}.`); setReject(null); setNote(''); state.reload(); onChange(); }
    catch (e) { toast(e.message, 'error'); }
  };
  return (
    <>
      <Async state={state}>{rows => rows.length === 0 ? <p className="muted small">No requests yet.</p> : (
        <ul className="rows">
          {rows.map(r => (
            <li key={r.id}>
              <div>
                <strong>{r.counterpart}</strong> <small className="muted">· {r.quantity} {r.unit} · {fmtDate(r.createdAt)}</small>
                <p className="small">Purpose: {r.purpose}<br />Registration no.: <strong>{r.registrationNumber}</strong></p>
                {r.phone && <p className="small">📞 {r.phone}</p>}
                {r.decisionNote && <p className="note">“{r.decisionNote}”</p>}
              </div>
              <div className="row-gap">
                <ReqBadge s={r.status} />
                {r.status === 'Pending' && <><button className="btn btn-sm btn-primary" onClick={() => act(r, 'APPROVED')}>Approve</button><button className="btn btn-sm btn-ghost" onClick={() => setReject(r)}>Reject</button></>}
                {r.status === 'Approved' && <><button className="btn btn-sm btn-primary" onClick={() => act(r, 'COLLECTED')}>Mark collected</button><button className="btn btn-sm btn-ghost" onClick={() => setReject(r)}>Cancel handover</button></>}
              </div>
            </li>
          ))}
        </ul>
      )}</Async>
      {reject && (
        <Modal title="Reject request" onClose={() => setReject(null)} footer={<><button className="btn btn-ghost" onClick={() => setReject(null)}>Back</button><button className="btn btn-danger" onClick={() => act(reject, 'REJECTED', note.trim())}>Reject</button></>}>
          <textarea rows="3" maxLength="500" placeholder="Reason (optional, shared with the requester)" value={note} onChange={e => setNote(e.target.value)} />
        </Modal>
      )}
    </>
  );
}

export default function MediMine() {
  const toast = useToast();
  const state = useFetch(() => api('/medishare/donations/mine'));
  const [open, setOpen] = useState(null);
  const withdraw = async d => {
    if (!window.confirm(`Withdraw “${d.medicineName}”? Pending requests will be rejected.`)) return;
    try { await del(`/medishare/donations/${d.id}`); toast('Listing withdrawn.'); state.reload(); } catch (e) { toast(e.message, 'error'); }
  };
  return (
    <>
      <PageHead title="My listings" subtitle="Your donated items and the requests for them.">
        <Link to="/medishare/donate" className="btn btn-primary">➕ Donate items</Link>
      </PageHead>
      <Async state={state}>{rows => rows.length === 0 ? <Empty icon="💊" title="No listings yet"><Link to="/medishare/donate">Donate your first items</Link></Empty> : (
        <div className="stack">
          {rows.map(d => (
            <div className="card" key={d.id}>
              <div className="row-between">
                <div>
                  <h3>{d.medicineName} {d.strength && <small className="muted">{d.strength}</small>}</h3>
                  <p className="muted small">{d.category} · batch {d.batchNumber} · expires {fmtDate(d.expiryDate)} ({d.daysToExpiry} days) · {d.quantityLeft} of {d.quantity} {d.unit} left · {d.pendingRequests || 0} request(s) waiting</p>
                  {d.reviewNote && <p className="note">Admin note: {d.reviewNote}</p>}
                </div>
                <div className="row-gap">
                  <MediBadge d={d} />
                  <button className="btn btn-sm btn-ghost" onClick={() => setOpen(open === d.id ? null : d.id)}>{open === d.id ? 'Hide requests' : 'Requests'}</button>
                  {!['WITHDRAWN', 'REJECTED'].includes(d.status) && <button className="btn btn-sm btn-danger" onClick={() => withdraw(d)}>Withdraw</button>}
                </div>
              </div>
              {open === d.id && <Incoming id={d.id} onChange={state.reload} />}
            </div>
          ))}
        </div>
      )}</Async>
    </>
  );
}
