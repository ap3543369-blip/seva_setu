import { useState } from 'react';
import { Link, useSearchParams } from 'react-router-dom';
import { api, post } from '../../api.js';
import { useToast } from '../../toast.jsx';
import { fmt, fmtDate, fmtDateTime, useFetch } from '../../utils.js';
import { Async, Avatar, Empty, Modal, PageHead, Tabs } from '../../components/ui.jsx';

const TYPE = { ngo: ['🤝', 'NGO application'], food: ['🍛', 'Food'], book: ['📚', 'Book'], medicine: ['💊', 'Medicine'] };
const TAB_OF = { ngo: 'ngo', food: 'food', book: 'books', medicine: 'medicine' };

/** Asks for a reason, then calls onConfirm(note). Used for every rejection. */
function ReasonModal({ title, hint, onClose, onConfirm, busy }) {
  const [note, setNote] = useState('');
  return (
    <Modal title={title} onClose={onClose} footer={<><button className="btn btn-ghost" onClick={onClose}>Back</button><button className="btn btn-danger" disabled={busy || !note.trim()} onClick={() => onConfirm(note.trim())}>Confirm</button></>}>
      <p className="muted">{hint}</p>
      <textarea rows="3" maxLength="500" value={note} onChange={e => setNote(e.target.value)} placeholder="Reason (required)" autoFocus />
    </Modal>
  );
}

function Overview({ summary, go }) {
  return (
    <>
      <div className="stat-grid four">
        {[['ngo', 'NGO applications', summary.ngoApplications], ['food', 'Food donations', summary.food], ['book', 'Book listings', summary.books], ['medicine', 'Medicine listings', summary.medicine]].map(([k, label, n]) => (
          <button key={k} className="stat stat-btn" onClick={() => go(TAB_OF[k])}>
            <span className="stat-icon" aria-hidden="true">{TYPE[k][0]}</span>
            <div><div className="stat-value">{fmt(n)}</div><div className="stat-label">{label} waiting</div></div>
          </button>
        ))}
      </div>
      <div className="card">
        <h3>Latest submissions waiting for you</h3>
        {summary.recent.length === 0 ? <Empty icon="🎉" title="Nothing is waiting">New submissions from donors and NGOs will appear here.</Empty> : (
          <ul className="rows">
            {summary.recent.map(r => (
              <li key={`${r.type}-${r.id}`}>
                <span className={`type-pill ${r.type}`}>{TYPE[r.type][0]} {TYPE[r.type][1]}</span>
                <div><strong>{r.title}</strong><br /><small className="muted">from {r.by} · {fmtDateTime(r.at)}</small></div>
                <button className="btn btn-sm btn-primary" onClick={() => go(TAB_OF[r.type])}>Review</button>
              </li>
            ))}
          </ul>
        )}
      </div>
    </>
  );
}

function NgoApplications({ onChange }) {
  const toast = useToast();
  const [status, setStatus] = useState('submitted');
  const state = useFetch(() => api(`/admin/ngo-applications?status=${status}`), [status]);
  const [open, setOpen] = useState(null);
  const [reject, setReject] = useState(null);
  const [busy, setBusy] = useState(false);
  const decide = async (a, decision, note) => {
    setBusy(true);
    try { await post(`/admin/ngo-applications/${a.id}/review`, { decision, note }); toast(decision === 'approve' ? `${a.orgName} verified.` : 'Sent back for changes.'); setOpen(null); setReject(null); state.reload(); onChange(); }
    catch (e) { toast(e.message, 'error'); } finally { setBusy(false); }
  };
  return (
    <>
      <div className="chips" style={{ marginBottom: '1rem' }}>
        {[['submitted', 'Under review'], ['rejected', 'Changes needed'], ['approved', 'Approved'], ['all', 'All']].map(([k, l]) => <button key={k} className={`chip-btn ${status === k ? 'on' : ''}`} onClick={() => setStatus(k)}>{l}</button>)}
      </div>
      <Async state={state}>{rows => rows.length === 0 ? <Empty icon="🤝" title="No applications here" /> : (
        <div className="stack">
          {rows.map(a => (
            <div className="card" key={a.id}>
              <div className="row-between">
                <div className="cell-user"><Avatar name={a.orgName} /><div><strong>{a.orgName}</strong> <span className="chip">{a.orgKind}</span><br /><small className="muted">Reg. {a.registrationNumber} · {a.city}, {a.state} · submitted {fmtDate(a.submittedAt)}</small></div></div>
                <div className="row-gap">
                  <span className={`badge ${a.status === 'APPROVED' ? 'ok' : a.status === 'REJECTED' ? 'bad' : 'warn'}`}>{a.statusLabel}</span>
                  <button className="btn btn-sm btn-ghost" onClick={() => setOpen(a)}>View details</button>
                  {a.status === 'SUBMITTED' && <><button className="btn btn-sm btn-primary" disabled={busy} onClick={() => decide(a, 'approve')}>Approve</button><button className="btn btn-sm btn-danger" onClick={() => setReject(a)}>Request changes</button></>}
                </div>
              </div>
            </div>
          ))}
        </div>
      )}</Async>
      {open && (
        <Modal title={open.orgName} wide onClose={() => setOpen(null)} footer={open.status === 'SUBMITTED' ? <><button className="btn btn-danger" onClick={() => setReject(open)}>Request changes</button><button className="btn btn-primary" disabled={busy} onClick={() => decide(open, 'approve')}>Approve & verify</button></> : <button className="btn btn-primary" onClick={() => setOpen(null)}>Close</button>}>
          <dl className="facts">
            {[['Type', open.orgKind], ['Registration no.', open.registrationNumber], ['Established', open.yearEstablished], ['People served', fmt(open.beneficiariesServed)], ['Contact person', open.contactPerson], ['Mobile', open.contactPhone], ['Login email', open.email], ['Website', open.website || '—'], ['Address', `${open.address}, ${open.city}, ${open.state} ${open.pincode}`], ['Works on', open.focusAreas], ['Areas served', open.serviceAreas || '—']].map(([k, v]) => <div key={k}><dt>{k}</dt><dd>{v}</dd></div>)}
            <div><dt>Certificate</dt><dd>{open.hasDocument ? <a href={`/api/admin/ngo-applications/${open.id}/document`} target="_blank" rel="noreferrer">Open uploaded document ↗</a> : <em className="muted">No document (demo account)</em>}</dd></div>
          </dl>
          {open.reviewNote && <div className="alert info"><span>Last note: {open.reviewNote}</span></div>}
        </Modal>
      )}
      {reject && <ReasonModal title={`Request changes from ${reject.orgName}`} hint="The NGO sees this note and can edit and send the application again." busy={busy} onClose={() => setReject(null)} onConfirm={n => decide(reject, 'reject', n)} />}
    </>
  );
}

function Submissions({ kind, onChange }) {
  const toast = useToast();
  const [status, setStatus] = useState('pending');
  const path = kind === 'food' ? 'food' : 'books';
  const state = useFetch(() => api(`/admin/approvals/${path}?status=${status}`), [status, path]);
  const [reject, setReject] = useState(null);
  const [busy, setBusy] = useState(false);
  const decide = async (x, decision, note) => {
    setBusy(true);
    try { await post(`/admin/approvals/${path}/${x.id}`, { decision, note }); toast(decision === 'approve' ? 'Approved. It is now live.' : 'Rejected. The donor was told why.'); setReject(null); state.reload(); onChange(); }
    catch (e) { toast(e.message, 'error'); } finally { setBusy(false); }
  };
  const title = x => (kind === 'food' ? `${x.food} · ${x.qty}` : `${x.title} · ${x.quantity} cop${x.quantity === 1 ? 'y' : 'ies'}`);
  return (
    <>
      <div className="chips" style={{ marginBottom: '1rem' }}>
        {[['pending', 'Waiting'], ['all', 'All reviewed + waiting']].map(([k, l]) => <button key={k} className={`chip-btn ${status === k ? 'on' : ''}`} onClick={() => setStatus(k)}>{l}</button>)}
      </div>
      <Async state={state}>{rows => rows.length === 0 ? <Empty icon={kind === 'food' ? '🍛' : '📚'} title="Nothing here" /> : (
        <div className="stack">
          {rows.map(x => (
            <div className="card approval-card" key={x.id}>
              <div className="row-between">
                <div>
                  <h3>{title(x)} <span className={`badge ${x.approval === 'APPROVED' ? 'ok' : x.approval === 'REJECTED' ? 'bad' : 'warn'}`}>{x.approvalLabel}</span></h3>
                  <p className="small"><strong>From:</strong> {x.donor} ({x.donorType}) · {x.donorEmail} · 📞 {x.donorPhone || '—'}</p>
                  <p className="small muted">{kind === 'food' ? `${x.category} · ${x.meals} meals · ${x.points} points on approval` : `${x.subject} · ${x.level} · ${x.condition}${x.author ? ` · ${x.author}` : ''}`} · {x.address}</p>
                  {x.description && <p className="note">“{x.description}”</p>}
                  {x.reviewNote && <p className="small"><strong>Admin note:</strong> {x.reviewNote}</p>}
                  <small className="muted">Submitted {fmtDateTime(x.createdAt)}</small>
                </div>
                {x.approval === 'PENDING' && <div className="row-gap"><button className="btn btn-sm btn-primary" disabled={busy} onClick={() => decide(x, 'approve')}>Approve</button><button className="btn btn-sm btn-danger" onClick={() => setReject(x)}>Reject</button></div>}
              </div>
              {x.images?.length > 0 && <div className="thumbs-row">{x.images.map(p => <a key={p} href={p} target="_blank" rel="noreferrer"><img src={p} alt="Donation" /></a>)}</div>}
            </div>
          ))}
        </div>
      )}</Async>
      {reject && <ReasonModal title={`Reject “${title(reject)}”`} hint="The donor is notified with this reason." busy={busy} onClose={() => setReject(null)} onConfirm={n => decide(reject, 'reject', n)} />}
    </>
  );
}

export default function AdminApprovals() {
  const [params, setParams] = useSearchParams();
  const tab = params.get('tab') || 'overview';
  const summary = useFetch(() => api('/admin/approvals/summary'));
  const go = t => setParams({ tab: t });
  const s = summary.data;
  return (
    <>
      <PageHead title="Approvals" subtitle="Everything that waits for your decision, grouped by type. Nothing goes live until you approve it." />
      <Tabs value={tab} onChange={go} tabs={[{ key: 'overview', label: 'Overview', count: s?.total }, { key: 'ngo', label: 'NGO applications', count: s?.ngoApplications }, { key: 'food', label: 'Food', count: s?.food }, { key: 'books', label: 'Books', count: s?.books }, { key: 'medicine', label: 'Medicines', count: s?.medicine }]} />
      {tab === 'overview' && <Async state={summary}>{d => <Overview summary={d} go={go} />}</Async>}
      {tab === 'ngo' && <NgoApplications onChange={summary.reload} />}
      {tab === 'food' && <Submissions kind="food" onChange={summary.reload} />}
      {tab === 'books' && <Submissions kind="books" onChange={summary.reload} />}
      {tab === 'medicine' && (
        <div className="card center-note">
          <p>{s ? `${s.medicine} medicine listing(s) are waiting.` : ''} Medicine listings are reviewed under MediShare Control, where you also set the safety rules and see the audit trail.</p>
          <Link className="btn btn-primary" to="/admin/medishare">Open MediShare Control</Link>
        </div>
      )}
    </>
  );
}
