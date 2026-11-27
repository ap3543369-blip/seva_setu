import { useMemo, useState } from 'react';
import { api, del, post } from '../../api.js';
import { useToast } from '../../toast.jsx';
import { monthLabel, useFetch } from '../../utils.js';
import { Async, Empty, Field, PageHead } from '../../components/ui.jsx';

export default function EcoRecords() {
  const toast = useToast();
  const cats = useFetch(() => api('/ecoai/categories'));
  const list = useFetch(() => api('/ecoai/records'));
  const thisMonth = useMemo(() => new Date().toISOString().slice(0, 7), []);
  const [f, setF] = useState({ month: '', category: '', kg: '' });
  const [errors, setErrors] = useState({});
  const set = k => e => { setF(x => ({ ...x, [k]: e.target.value })); setErrors(x => ({ ...x, [k]: undefined })); };

  const save = async e => {
    e.preventDefault();
    const er = {};
    if (!/^\d{4}-(0[1-9]|1[0-2])$/.test(f.month)) er.month = 'Choose a month.'; else if (f.month > thisMonth) er.month = 'Month cannot be in the future.';
    if (!f.category) er.category = 'Choose a category.';
    if (f.kg === '' || +f.kg < 0) er.kg = 'Enter the kg collected.';
    setErrors(er);
    if (Object.keys(er).length) return;
    try { await post('/ecoai/records', { month: f.month, category: f.category, kg: +f.kg }); toast('Record saved.'); setF(x => ({ ...x, kg: '' })); list.reload(); }
    catch (ex) { setErrors(ex.errors || {}); toast(ex.message, 'error'); }
  };
  const remove = async r => { try { await del(`/ecoai/records/${r.id}`); toast('Record deleted.'); list.reload(); } catch (e) { toast(e.message, 'error'); } };
  const months = new Set((list.data || []).map(r => r.month)).size;

  return (
    <>
      <PageHead title="E-waste records" subtitle="Your collection history. The forecast learns from these numbers." />
      <div className="alert info">You have {months} month(s) of history. The forecast needs at least 4; 24+ months gives the best results. Saving a month + category again replaces its value.</div>
      <form className="card" onSubmit={save} noValidate>
        <div className="form-grid three">
          <Field label="Month *" error={errors.month}><input type="month" max={thisMonth} value={f.month} onChange={set('month')} /></Field>
          <Field label="Category *" error={errors.category}><select value={f.category} onChange={set('category')}><option value="">Choose…</option>{(cats.data || []).map(c => <option key={c}>{c}</option>)}</select></Field>
          <Field label="kg collected *" error={errors.kg}><input type="number" min="0" step="any" value={f.kg} onChange={set('kg')} /></Field>
        </div>
        <button className="btn btn-primary">Save record</button>
      </form>
      <Async state={list}>{rows => rows.length === 0 ? <Empty icon="🗂️" title="No records yet">Add your past months above.</Empty> : (
        <div className="card table-wrap top-gap"><table className="table">
          <thead><tr><th>Month</th><th>Category</th><th>kg</th><th /></tr></thead>
          <tbody>{rows.map(r => (
            <tr key={r.id}><td data-label="Month">{monthLabel(r.month)}</td><td data-label="Category">{r.category}</td><td data-label="kg">{r.kg} {r.demo && <span className="badge info">demo</span>}</td>
              <td className="actions"><button className="btn btn-sm btn-danger" onClick={() => remove(r)}>Delete</button></td></tr>
          ))}</tbody></table></div>
      )}</Async>
    </>
  );
}
