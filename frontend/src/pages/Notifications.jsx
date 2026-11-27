import { api, post } from '../api.js';
import { fmtDateTime, useFetch } from '../utils.js';
import { Async, Empty, PageHead } from '../components/ui.jsx';

export default function Notifications() {
  const state = useFetch(() => api('/notifications'));
  const readAll = async () => {
    await post('/notifications/read-all');
    window.dispatchEvent(new Event('notifications-read'));
    state.reload();
  };
  return (
    <>
      <PageHead title="Notifications" subtitle="Requests, acceptances, deliveries and verification updates.">
        <button className="btn btn-ghost" onClick={readAll} disabled={!state.data?.unread}>Mark all as read</button>
      </PageHead>
      <Async state={state}>{d => d.items.length ? (
        <ul className="notif card">
          {d.items.map(n => <li key={n.id} className={n.seen ? '' : 'unseen'}><span className="dot" /><div><p>{n.message}</p><small className="muted">{fmtDateTime(n.createdAt)}</small></div></li>)}
        </ul>
      ) : <Empty icon="🔔" title="You’re all caught up">New activity will show up here.</Empty>}</Async>
    </>
  );
}
