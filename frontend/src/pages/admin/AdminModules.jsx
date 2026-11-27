import { api, post } from '../../api.js';
import { useToast } from '../../toast.jsx';
import { useFetch } from '../../utils.js';
import { Async, PageHead } from '../../components/ui.jsx';

export default function AdminModules() {
  const toast = useToast();
  const state = useFetch(() => api('/admin/modules'));
  const toggle = async m => {
    try { const r = await post(`/admin/modules/${m.key}/toggle`); toast(`${r.label} is now ${r.enabled ? 'ON' : 'OFF'}.`); state.reload(); }
    catch (e) { toast(e.message, 'error'); }
  };
  const toggleApproval = async m => {
    try { const r = await post(`/admin/modules/${m.key}/approval-toggle`); toast(`Admin approval for ${r.label} is now ${r.requireApproval ? 'REQUIRED' : 'OFF'}.`); state.reload(); }
    catch (e) { toast(e.message, 'error'); }
  };
  return (
    <>
      <PageHead title="Modules" subtitle="Switch modules on or off, and decide whether new donations need your approval before they go live." />
      <Async state={state}>{rows => (
        <div className="card">
          <ul className="rows">
            {rows.map(m => (
              <li key={m.key}>
                <div><strong>{m.label}</strong><br /><small className="muted">{m.enabled ? 'Live for users with access' : 'Hidden: shown as “Coming soon”'}</small></div>
                <div className="row-gap">
                  {m.approvalApplies && (
                    <label className="row-gap small" title="When on, new donations stay hidden until you approve them">
                      <span>Admin approval</span>
                      <button className={`switch ${m.requireApproval ? 'on' : ''}`} role="switch" aria-checked={m.requireApproval} aria-label={`${m.label} admin approval on/off`} onClick={() => toggleApproval(m)}><i /></button>
                    </label>
                  )}
                  <button className={`switch ${m.enabled ? 'on' : ''}`} role="switch" aria-checked={m.enabled} aria-label={`${m.label} on/off`} onClick={() => toggle(m)}><i /></button>
                </div>
              </li>
            ))}
          </ul>
          <p className="muted small">MediShare listings are always reviewed under MediShare Control. NGOs must always be verified before they can use any module. Per-user access: Users → Modules.</p>
        </div>
      )}</Async>
    </>
  );
}
