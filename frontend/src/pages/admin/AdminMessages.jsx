import { useState } from 'react';
import { api, post } from '../../api.js';
import { useToast } from '../../toast.jsx';
import { fmtDateTime, useFetch } from '../../utils.js';
import { Async, Empty, PageHead, Tabs } from '../../components/ui.jsx';

export default function AdminMessages() {
  const toast = useToast();
  const state = useFetch(() => api('/admin/messages'));
  const [tab, setTab] = useState('open');
  const list = state.data || [];
  const shown = list.filter(m => (tab === 'open' ? !m.handled : tab === 'done' ? m.handled : true));
  const toggle = async m => { try { await post(`/admin/messages/${m.id}/handled`); state.reload(); } catch (e) { toast(e.message, 'error'); } };
  return (
    <>
      <PageHead title="Messages" subtitle="Contact-form messages from visitors." />
      <Tabs value={tab} onChange={setTab} tabs={[{ key: 'open', label: 'Open', count: list.filter(m => !m.handled).length }, { key: 'done', label: 'Handled', count: list.filter(m => m.handled).length }, { key: 'all', label: 'All', count: list.length }]} />
      <Async state={state}>{() => shown.length === 0 ? <Empty icon="✉️" title="No messages here" /> : (
        <div className="msgs">
          {shown.map(m => (
            <article key={m.id} className={`card msg ${m.handled ? 'done' : ''}`}>
              <div className="row-between"><h3>{m.subject}</h3><small className="muted">{fmtDateTime(m.createdAt)}</small></div>
              <p className="muted small">From <strong>{m.name}</strong> · {m.email}</p>
              <p>{m.message}</p>
              <div className="row-gap">
                <a className="btn btn-sm btn-ghost" href={`mailto:${m.email}?subject=${encodeURIComponent(`Re: ${m.subject}`)}`}>↩ Reply by email</a>
                <button className={`btn btn-sm ${m.handled ? 'btn-ghost' : 'btn-primary'}`} onClick={() => toggle(m)}>{m.handled ? 'Mark as open' : '✓ Mark handled'}</button>
              </div>
            </article>
          ))}
        </div>
      )}</Async>
    </>
  );
}
