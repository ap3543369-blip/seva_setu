import { Link } from 'react-router-dom';
import { api, patch } from '../../api.js';
import { useToast } from '../../toast.jsx';
import { fmtDate, useFetch } from '../../utils.js';
import { Async, Empty, PageHead } from '../../components/ui.jsx';
import { ReqBadge } from './MediMine.jsx';

export default function MediRequests() {
  const toast = useToast();
  const state = useFetch(() => api('/medishare/requests/mine'));
  const cancel = async r => {
    try { await patch(`/medishare/requests/${r.id}/status`, { status: 'CANCELLED' }); toast('Request cancelled.'); state.reload(); } catch (e) { toast(e.message, 'error'); }
  };
  return (
    <>
      <PageHead title="My requests" subtitle="Pickup details appear once the donor approves." />
      <Async state={state}>{rows => rows.length === 0 ? <Empty icon="📋" title="No requests yet"><Link to="/medishare/browse">Find items</Link> to send a request.</Empty> : (
        <div className="card table-wrap">
          <table className="table">
            <thead><tr><th>Date</th><th>Item</th><th>Qty</th><th>Donor</th><th>Status</th><th>Pickup</th><th /></tr></thead>
            <tbody>
              {rows.map(r => (
                <tr key={r.id}>
                  <td data-label="Date">{fmtDate(r.createdAt)}</td><td data-label="Item"><strong>{r.medicine}</strong></td>
                  <td data-label="Qty">{r.quantity} {r.unit}</td><td data-label="Donor">{r.counterpart}</td>
                  <td data-label="Status"><ReqBadge s={r.status} />{r.decisionNote && <><br /><small className="muted">{r.decisionNote}</small></>}</td>
                  <td data-label="Pickup">{r.phone || r.address ? <small>{r.phone && <>📞 {r.phone}<br /></>}{r.address}</small> : <span className="muted">—</span>}</td>
                  <td className="actions">{(r.status === 'Pending' || r.status === 'Approved') && <button className="btn btn-sm btn-danger" onClick={() => cancel(r)}>Cancel</button>}</td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      )}</Async>
    </>
  );
}
