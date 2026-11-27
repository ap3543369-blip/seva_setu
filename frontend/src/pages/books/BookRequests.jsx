import { Link } from 'react-router-dom';
import { api, patch } from '../../api.js';
import { useToast } from '../../toast.jsx';
import { fmtDate, useFetch } from '../../utils.js';
import { Async, Empty, PageHead, StatusBadge } from '../../components/ui.jsx';

export default function BookRequests() {
  const toast = useToast();
  const state = useFetch(() => api('/books/requests/mine'));
  const cancel = async r => {
    try { await patch(`/books/requests/${r.id}/status`, { status: 'CANCELLED' }); toast('Request cancelled.'); state.reload(); } catch (e) { toast(e.message, 'error'); }
  };
  return (
    <>
      <PageHead title="My book requests" subtitle="Contact details appear here once a donor accepts your request." />
      <Async state={state}>{rows => rows.length === 0 ? <Empty icon="📋" title="No requests yet"><Link to="/books">Browse books</Link> and send your first request.</Empty> : (
        <div className="card table-wrap">
          <table className="table">
            <thead><tr><th>Date</th><th>Book</th><th>Copies</th><th>Donor</th><th>Status</th><th>Contact</th><th /></tr></thead>
            <tbody>
              {rows.map(r => (
                <tr key={r.id}>
                  <td data-label="Date">{fmtDate(r.createdAt)}</td>
                  <td data-label="Book"><Link to={`/books/${r.listingId}`}><strong>{r.title}</strong></Link><br /><small className="muted">{r.subject}</small></td>
                  <td data-label="Copies">{r.quantity}</td>
                  <td data-label="Donor">{r.counterpart}</td>
                  <td data-label="Status"><StatusBadge status={r.status} /></td>
                  <td data-label="Contact">{r.phone || r.address ? <small>{r.phone && <>📞 {r.phone}<br /></>}{r.address}</small> : <span className="muted">—</span>}</td>
                  <td className="actions">{(r.status === 'Pending' || r.status === 'Accepted') && <button className="btn btn-sm btn-danger" onClick={() => cancel(r)}>Cancel</button>}</td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      )}</Async>
    </>
  );
}
