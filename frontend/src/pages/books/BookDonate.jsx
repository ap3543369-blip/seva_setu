import { useEffect, useMemo, useRef, useState } from 'react';
import { useNavigate } from 'react-router-dom';
import { api } from '../../api.js';
import { useToast } from '../../toast.jsx';
import { BOOK_CONDITIONS, LEVELS, SUBJECTS } from '../../constants.js';
import { Field, PageHead } from '../../components/ui.jsx';

const EMPTY = { title: '', author: '', subject: '', level: '', language: 'English', condition: 'Good', quantity: 1, description: '', city: '', pincode: '', address: '', phone: '' };

export default function BookDonate() {
  const toast = useToast();
  const nav = useNavigate();
  const [f, setF] = useState(EMPTY);
  const [files, setFiles] = useState([]);
  const [errors, setErrors] = useState({});
  const [busy, setBusy] = useState(false);
  const input = useRef(null);
  const previews = useMemo(() => files.map(x => URL.createObjectURL(x)), [files]);
  useEffect(() => () => previews.forEach(URL.revokeObjectURL), [previews]);
  const set = k => e => { setF(x => ({ ...x, [k]: e.target.value })); setErrors(x => ({ ...x, [k]: undefined })); };

  const validate = () => {
    const e = {};
    if (!f.title.trim()) e.title = 'Enter the book title.';
    if (!f.subject) e.subject = 'Choose a subject.';
    if (!f.level) e.level = 'Choose the class / level.';
    if (!(+f.quantity >= 1 && +f.quantity <= 500)) e.quantity = 'Enter 1 to 500 copies.';
    if (!f.city.trim()) e.city = 'City is required.';
    if (!/^\d{6}$/.test(f.pincode)) e.pincode = 'Enter a 6-digit PIN code.';
    if (!f.address.trim()) e.address = 'Pickup address is required.';
    if (!/^[6-9]\d{9}$/.test(f.phone)) e.phone = 'Enter a valid 10-digit mobile number.';
    setErrors(e);
    return Object.keys(e).length === 0;
  };

  const addFiles = e => {
    const picked = [...e.target.files]; e.target.value = '';
    const ok = picked.filter(p => p.type.startsWith('image/') && p.size <= 5 * 1024 * 1024);
    if (ok.length < picked.length) toast('Only image files up to 5 MB are allowed.', 'error');
    setFiles(c => [...c, ...ok].slice(0, 4));
  };

  const submit = async e => {
    e.preventDefault();
    if (!validate()) return;
    setBusy(true);
    try {
      const fd = new FormData();
      fd.append('data', new Blob([JSON.stringify({ ...f, quantity: +f.quantity })], { type: 'application/json' }));
      files.forEach(x => fd.append('images', x));
      const created = await api('/books', { method: 'POST', form: fd });
      toast(created.status === 'Awaiting approval' ? 'Sent to the admin for approval. It goes live once approved.' : 'Books listed. Thank you!'); nav('/books/mine');
    } catch (ex) { setErrors(ex.errors || {}); toast(ex.message, 'error'); } finally { setBusy(false); }
  };

  return (
    <>
      <PageHead title="Donate books" subtitle="List textbooks, storybooks or study material for students who need them." />
      <form className="card" onSubmit={submit} noValidate>
        <div className="form-grid">
          <Field label="Title *" error={errors.title}><input value={f.title} onChange={set('title')} maxLength="150" /></Field>
          <Field label="Author" error={errors.author}><input value={f.author} onChange={set('author')} maxLength="120" /></Field>
          <Field label="Subject *" error={errors.subject}><select value={f.subject} onChange={set('subject')}><option value="">Choose…</option>{SUBJECTS.map(s => <option key={s}>{s}</option>)}</select></Field>
          <Field label="Class / level *" error={errors.level}><select value={f.level} onChange={set('level')}><option value="">Choose…</option>{LEVELS.map(s => <option key={s}>{s}</option>)}</select></Field>
          <Field label="Language" error={errors.language}><input value={f.language} onChange={set('language')} /></Field>
          <Field label="Copies *" error={errors.quantity}><input type="number" min="1" max="500" value={f.quantity} onChange={set('quantity')} /></Field>
        </div>
        <fieldset className="field"><legend className="field-label">Condition *</legend>
          <div className="radio-row">{BOOK_CONDITIONS.map(c => <label key={c} className={`radio ${f.condition === c ? 'on' : ''}`}><input type="radio" name="cond" checked={f.condition === c} onChange={() => setF(x => ({ ...x, condition: c }))} />{c}</label>)}</div>
        </fieldset>
        <Field label="Description" error={errors.description}><textarea rows="3" maxLength="1000" value={f.description} onChange={set('description')} placeholder="Edition, marks, missing pages…" /></Field>
        <div className="form-grid">
          <Field label="City *" error={errors.city}><input value={f.city} onChange={set('city')} /></Field>
          <Field label="PIN code *" error={errors.pincode}><input inputMode="numeric" maxLength="6" value={f.pincode} onChange={set('pincode')} /></Field>
        </div>
        <Field label="Pickup address *" error={errors.address} hint="Shown only to a requester you accept"><textarea rows="2" value={f.address} onChange={set('address')} /></Field>
        <Field label="Contact mobile *" error={errors.phone} hint="Shown only to a requester you accept"><input inputMode="numeric" maxLength="10" value={f.phone} onChange={set('phone')} /></Field>
        <div className="field"><span className="field-label">Photos (optional, up to 4)</span>
          <div className="uploader">
            {previews.map((p, i) => <div key={p} className="thumb"><img src={p} alt={`Upload ${i + 1}`} /><button type="button" aria-label="Remove photo" onClick={() => setFiles(l => l.filter((_, j) => j !== i))}>✕</button></div>)}
            {files.length < 4 && <button type="button" className="thumb add" onClick={() => input.current.click()}><span>＋</span>Add</button>}
            <input ref={input} type="file" accept="image/*" multiple hidden onChange={addFiles} />
          </div>
        </div>
        <button className="btn btn-primary" disabled={busy}>{busy ? 'Saving…' : '✔ List these books'}</button>
      </form>
    </>
  );
}
