import { useEffect, useState } from 'react';
import { api, post, put } from '../../api.js';
import { useToast } from '../../toast.jsx';
import { downloadCsv, fmtDate, fmtDateTime, useFetch } from '../../utils.js';
import { Async, Empty, Field, Modal, PageHead, Tabs } from '../../components/ui.jsx';
import { MediBadge } from '../medi/MediMine.jsx';

function Review() {
  const toast = useToast();
  const state = useFetch(() => api('/admin/medishare/donations'));
  const [target, setTarget] = useState(null); // { d, decision }
  const [note, setNote] = useState('');
  const [busy, setBusy] = useState(false);
  const decide = async (d, decision, n) => {
    setBusy(true);
    try { await post(`/admin/medishare/donations/${d.id}/review`, { decision, note: n || undefined }); toast(`Item ${{ approve: 'approved', reject: 'rejected', suspend: 'suspended' }[decision]}.`); setTarget(null); setNote(''); state.reload(); }
    catch (e) { toast(e.message, 'error'); } finally { setBusy(false); }
  };
  return (
    <>
      <Async state={state}>{rows => rows.length === 0 ? <Empty icon="💊" title="Nothing to review" /> : (
        <div className="stack">
          {rows.map(d => (
            <div className="card" key={d.id}>
              <div className="row-between">
                <div>
                  <h3>{d.medicineName} {d.strength && <small className="muted">{d.strength}</small>}</h3>
                  <p className="muted small">{d.category} · from <strong>{d.donor}</strong> ({d.city}) · licence <strong>{d.licenceNumber}</strong></p>
                  <p className="small">Batch {d.batchNumber} · expires {fmtDate(d.expiryDate)} ({d.daysToExpiry} days) · {d.quantity} {d.unit} · {d.storage} · {d.sealedOriginalPack ? 'sealed pack' : 'NOT sealed'}</p>
                  {d.notes && <p className="note">“{d.notes}”</p>}
                  {d.reviewNote && <p className="note">Admin note: {d.reviewNote}</p>}
                </div>
                <div className="row-gap">
                  <MediBadge d={d} />
                  {d.status === 'PENDING_REVIEW' && <><button className="btn btn-sm btn-primary" onClick={() => decide(d, 'approve')}>Approve</button><button className="btn btn-sm btn-danger" onClick={() => setTarget({ d, decision: 'reject' })}>Reject</button></>}
                  {d.status === 'LISTED' && <button className="btn btn-sm btn-danger" onClick={() => setTarget({ d, decision: 'suspend' })}>Suspend</button>}
                  {d.status === 'SUSPENDED' && <button className="btn btn-sm btn-primary" onClick={() => decide(d, 'approve')}>Re-list</button>}
                </div>
              </div>
            </div>
          ))}
        </div>
      )}</Async>
      {target && (
        <Modal title={`${target.decision === 'reject' ? 'Reject' : 'Suspend'} “${target.d.medicineName}”`} onClose={() => setTarget(null)} footer={<><button className="btn btn-ghost" onClick={() => setTarget(null)}>Back</button><button className="btn btn-danger" disabled={busy || !note.trim()} onClick={() => decide(target.d, target.decision, note.trim())}>Confirm</button></>}>
          <p className="muted">A reason is required and is sent to the donor.</p>
          <textarea rows="3" maxLength="500" value={note} onChange={e => setNote(e.target.value)} placeholder="Reason" />
        </Modal>
      )}
    </>
  );
}

function Policy() {
  const toast = useToast();
  const state = useFetch(() => api('/admin/medishare/policy'));
  const [f, setF] = useState(null);
  const [errors, setErrors] = useState({});
  useEffect(() => {
    const p = state.data;
    if (p) setF({ minShelfLifeDays: p.minShelfLifeDays, requireAdminReview: p.requireAdminReview, requireSealedPack: p.requireSealedPack, allowColdChain: p.allowColdChain, allowedCategories: p.allowedCategories.join(', '), blockedTerms: p.blockedTerms.join(', ') });
  }, [state.data]);
  if (!f) return <Async state={state}>{() => null}</Async>;
  const set = k => e => setF(x => ({ ...x, [k]: e.target.type === 'checkbox' ? e.target.checked : e.target.value }));
  const save = async e => {
    e.preventDefault();
    try { await put('/admin/medishare/policy', { ...f, minShelfLifeDays: +f.minShelfLifeDays }); toast('Policy saved.'); setErrors({}); state.reload(); }
    catch (ex) { setErrors(ex.errors || {}); toast(ex.message, 'error'); }
  };
  return (
    <form className="card" onSubmit={save}>
      <div className="alert warn"><span>⚠️ {state.data.notice}</span></div>
      <Field label="Minimum shelf life left (days)" error={errors.minShelfLifeDays} hint="0 to 1825. Default 180 is a placeholder: confirm it with a pharmacist / legal adviser."><input type="number" min="0" max="1825" value={f.minShelfLifeDays} onChange={set('minShelfLifeDays')} /></Field>
      <div className="stack">
        <label className="check"><input type="checkbox" checked={f.requireAdminReview} onChange={set('requireAdminReview')} /> Admin must review every listing before it goes live</label>
        <label className="check"><input type="checkbox" checked={f.requireSealedPack} onChange={set('requireSealedPack')} /> Only sealed, original-pack items</label>
        <label className="check"><input type="checkbox" checked={f.allowColdChain} onChange={set('allowColdChain')} /> Allow cold-chain (refrigerated) items</label>
      </div>
      <Field label="Allowed categories (comma separated)" error={errors.allowedCategories}><textarea rows="2" value={f.allowedCategories} onChange={set('allowedCategories')} /></Field>
      <Field label="Blocked terms (comma separated, matched in name / strength / notes)" error={errors.blockedTerms}><textarea rows="3" value={f.blockedTerms} onChange={set('blockedTerms')} /></Field>
      <button className="btn btn-primary">Save policy</button>
    </form>
  );
}

function Audit() {
  const state = useFetch(() => api('/admin/medishare/audit'));
  return (
    <Async state={state}>{rows => rows.length === 0 ? <Empty icon="🧾" title="No activity yet" /> : (
      <>
        <div className="row-gap" style={{ marginBottom: '.75rem' }}><button className="btn btn-ghost btn-sm" onClick={() => downloadCsv('medishare-audit.csv', [['When', 'Who', 'Role', 'Action', 'Target', 'Detail'], ...rows.map(a => [fmtDateTime(a.createdAt), a.actor, a.role, a.action, `${a.targetType || ''} ${a.targetId || ''}`, a.detail])])}>⬇ Export CSV</button></div>
        <div className="card table-wrap">
          <table className="table">
            <thead><tr><th>When</th><th>Who</th><th>Action</th><th>Detail</th></tr></thead>
            <tbody>{rows.map(a => <tr key={a.id}><td data-label="When">{fmtDateTime(a.createdAt)}</td><td data-label="Who">{a.actor} <small className="muted">({a.role})</small></td><td data-label="Action"><code>{a.action}</code></td><td data-label="Detail">{a.detail}</td></tr>)}</tbody>
          </table>
        </div>
      </>
    )}</Async>
  );
}

export default function AdminMedi() {
  const [tab, setTab] = useState('review');
  return (
    <>
      <PageHead title="MediShare control" subtitle="Review listings, set the safety policy and inspect the audit trail." />
      <Tabs value={tab} onChange={setTab} tabs={[{ key: 'review', label: 'Listings' }, { key: 'policy', label: 'Safety policy' }, { key: 'audit', label: 'Audit trail' }]} />
      {tab === 'review' && <Review />}
      {tab === 'policy' && <Policy />}
      {tab === 'audit' && <Audit />}
    </>
  );
}
