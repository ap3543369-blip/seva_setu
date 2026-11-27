import { Link } from 'react-router-dom';
import { api, patch } from '../../api.js';
import { useToast } from '../../toast.jsx';
import { fmtPickup, foodImage, useFetch } from '../../utils.js';
import { Async, Empty, PageHead } from '../../components/ui.jsx';

const COLS = [['Pending', '⏳'], ['Accepted', '🚚'], ['Delivered', '✅'], ['Declined', '✖️']];

export default function Requests() {
  const toast = useToast();
  const state = useFetch(() => api('/requests/mine'));
  const act = async (r, status) => {
    try { await patch(`/requests/${r.id}/status`, { status }); toast(status === 'Accepted' ? 'Request accepted.' : 'Marked as delivered!'); state.reload(); }
    catch (e) { toast(e.message, 'error'); }
  };
  return (
    <>
      <PageHead title="My requests" subtitle="Move each request from Pending to Delivered." />
      <Async state={state}>{rows => rows.length === 0 ? <Empty icon="📋" title="No requests yet"><Link to="/ngo/browse">Find food</Link> and send your first request.</Empty> : (
        <div className="board">
          {COLS.map(([status, icon]) => {
            const items = rows.filter(r => r.status === status);
            return (
              <section key={status} className="board-col">
                <h3>{icon} {status} <em>{items.length}</em></h3>
                {items.length === 0 && <p className="muted small">Nothing here.</p>}
                {items.map(r => (
                  <article className="card ticket" key={r.id}>
                    <img src={foodImage(r)} alt="" />
                    <div>
                      <Link to={`/donation/${r.donationId}`}><strong>{r.food}</strong></Link> <small>({r.qty})</small>
                      <p className="muted small">{r.donor} · {r.distanceKm} km<br />🕒 {fmtPickup(r.pickupAt)} · 🍛 {r.meals}</p>
                      {r.note && <p className="note">“{r.note}”</p>}
                    </div>
                    {status === 'Pending' && <button className="btn btn-sm btn-ghost" onClick={() => act(r, 'Accepted')}>Accept</button>}
                    {status === 'Accepted' && <button className="btn btn-sm btn-primary" onClick={() => act(r, 'Delivered')}>Mark delivered</button>}
                  </article>
                ))}
              </section>
            );
          })}
        </div>
      )}</Async>
    </>
  );
}
