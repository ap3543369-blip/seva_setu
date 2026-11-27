import { useEffect, useMemo, useRef, useState } from 'react';
import { Link } from 'react-router-dom';
import { api } from '../../api.js';
import { useToast } from '../../toast.jsx';
import { Field, PageHead } from '../../components/ui.jsx';

const STEPS = ['Food details', 'Location', 'Schedule', 'Review'];
const FOODS = ['Cooked Food', 'Packed Food', 'Bakery Items', 'Fruits & Vegetables', 'Raw Ingredients'];
const UNITS = ['kg', 'portions', 'packets', 'litres'];
const CONDITIONS = ['Fresh (Ready to eat)', 'Packed & Sealed', 'Uncooked (Raw)'];
const iso = d => d.toISOString().slice(0, 10);
const FIELD_STEP = { foodType: 0, quantity: 0, unit: 0, description: 0, condition: 0, bestDate: 0, bestTime: 0, address: 1, city: 1, pincode: 1, phone: 1, handover: 2, pickupDate: 2, timeFrom: 2, timeTo: 2, notes: 2 };
const EMPTY = { foodType: '', quantity: '', unit: 'kg', description: '', condition: CONDITIONS[0], bestDate: '', bestTime: '20:00', address: '', city: '', pincode: '', phone: '', handover: 'NGO pickup', pickupDate: '', timeFrom: '14:00', timeTo: '16:00', notes: '' };

export default function Donate() {
  const toast = useToast();
  const today = useMemo(() => iso(new Date()), []);
  const [step, setStep] = useState(0);
  const [f, setF] = useState(() => { try { return { ...EMPTY, ...JSON.parse(localStorage.getItem('fs_draft') || '{}') }; } catch (e) { return EMPTY; } });
  const [files, setFiles] = useState([]);
  const [errors, setErrors] = useState({});
  const [busy, setBusy] = useState(false);
  const [done, setDone] = useState(null);
  const fileInput = useRef(null);
  const previews = useMemo(() => files.map(file => URL.createObjectURL(file)), [files]);
  useEffect(() => () => previews.forEach(URL.revokeObjectURL), [previews]);
  useEffect(() => { localStorage.setItem('fs_draft', JSON.stringify(f)); }, [f]);
  const set = k => e => { setF(x => ({ ...x, [k]: e.target.value })); setErrors(x => ({ ...x, [k]: undefined })); };

  const check = s => {
    const e = {};
    if (s === 0) {
      if (!f.foodType) e.foodType = 'Choose a food type.';
      if (!(+f.quantity > 0)) e.quantity = 'Enter a quantity greater than 0.';
      if (!f.bestDate) e.bestDate = 'Choose a best-before date.'; else if (f.bestDate < today) e.bestDate = 'Date cannot be in the past.';
    }
    if (s === 1) {
      if (!f.address.trim()) e.address = 'Pickup address is required.';
      if (!f.city.trim()) e.city = 'City is required.';
      if (!/^\d{6}$/.test(f.pincode)) e.pincode = 'Enter a 6-digit PIN code.';
      if (!/^[6-9]\d{9}$/.test(f.phone)) e.phone = 'Enter a valid 10-digit mobile number.';
    }
    if (s === 2) {
      if (!f.pickupDate) e.pickupDate = 'Choose a pickup date.'; else if (f.pickupDate < today) e.pickupDate = 'Date cannot be in the past.';
      if (f.timeTo <= f.timeFrom) e.timeTo = 'End time must be after start time.';
    }
    setErrors(x => ({ ...x, ...e }));
    return Object.keys(e).length === 0;
  };
  const next = () => { if (check(step)) setStep(s => s + 1); };

  const addFiles = e => {
    const picked = [...e.target.files];
    e.target.value = '';
    const ok = picked.filter(p => p.type.startsWith('image/') && p.size <= 5 * 1024 * 1024);
    if (ok.length < picked.length) toast('Only image files up to 5 MB are allowed.', 'error');
    setFiles(cur => [...cur, ...ok].slice(0, 4));
  };

  const submit = async () => {
    if (!check(0) || !check(1) || !check(2)) { setStep([0, 1, 2].find(s => !check(s)) ?? 0); return; }
    setBusy(true);
    try {
      const payload = { ...f, quantity: +f.quantity };
      const fd = new FormData();
      fd.append('data', new Blob([JSON.stringify(payload)], { type: 'application/json' }));
      files.forEach(file => fd.append('images', file));
      const d = await api('/donations', { method: 'POST', form: fd });
      localStorage.removeItem('fs_draft');
      setDone(d);
    } catch (ex) {
      setErrors(ex.errors || {});
      const first = Object.keys(ex.errors || {}).map(k => FIELD_STEP[k]).filter(s => s !== undefined)[0];
      if (first !== undefined) setStep(first);
      toast(ex.message, 'error');
    } finally { setBusy(false); }
  };

  if (done) {
    return (
      <div className="card success-card">
        <div className="success-burst">🎉</div>
        <h2>{done.status === 'Awaiting approval' ? 'Sent for admin approval' : 'Thank you for donating!'}</h2>
        <p>Your <strong>{done.qty}</strong> of <strong>{done.food}</strong> {done.status === 'Awaiting approval' ? 'is waiting for the admin to verify it. It becomes visible to NGOs once approved, and you get your points then.' : 'is now visible to verified NGOs.'}</p>
        <div className="mini-stats"><div><b>{done.meals}</b><small>Meals</small></div><div><b>{done.status === 'Awaiting approval' ? `+${done.points} later` : `+${done.points}`}</b><small>Points</small></div></div>
        <div className="row-gap center">
          <Link className="btn btn-primary" to="/donor/donations">View my donations</Link>
          <button className="btn btn-ghost" onClick={() => { setDone(null); setF(EMPTY); setFiles([]); setStep(0); }}>Donate again</button>
        </div>
      </div>
    );
  }

  return (
    <>
      <PageHead title="Donate surplus food" subtitle="Fill in the details to help reduce food waste.">
        <button className="btn btn-ghost btn-sm" onClick={() => { setF(EMPTY); setFiles([]); setErrors({}); setStep(0); localStorage.removeItem('fs_draft'); }}>Clear form</button>
      </PageHead>
      <ol className="stepper">{STEPS.map((s, i) => <li key={s} className={i === step ? 'on' : i < step ? 'done' : ''}><span>{i < step ? '✓' : i + 1}</span>{s}</li>)}</ol>
      <div className="two-col wide-left">
        <div className="card">
          {step === 0 && (
            <>
              <div className="form-grid">
                <Field label="Food type *" error={errors.foodType}><select value={f.foodType} onChange={set('foodType')}><option value="">Select food type</option>{FOODS.map(x => <option key={x}>{x}</option>)}</select></Field>
                <Field label="Quantity (approx.) *" error={errors.quantity || errors.unit}>
                  <div className="input-group"><input type="number" min="1" step="any" value={f.quantity} onChange={set('quantity')} placeholder="20" /><select value={f.unit} onChange={set('unit')}>{UNITS.map(x => <option key={x}>{x}</option>)}</select></div>
                </Field>
              </div>
              <Field label="Description" hint="e.g. Veg Biryani, freshly prepared, packed in containers" error={errors.description}><textarea rows="3" maxLength="1000" value={f.description} onChange={set('description')} /></Field>
              <fieldset className="field"><legend className="field-label">Food condition *</legend>
                <div className="radio-row">{CONDITIONS.map(c => <label key={c} className={`radio ${f.condition === c ? 'on' : ''}`}><input type="radio" name="cond" checked={f.condition === c} onChange={() => setF(x => ({ ...x, condition: c }))} />{c}</label>)}</div>
              </fieldset>
              <div className="form-grid">
                <Field label="Best before (date) *" error={errors.bestDate}><input type="date" min={today} value={f.bestDate} onChange={set('bestDate')} /></Field>
                <Field label="Best before (time)" error={errors.bestTime}><input type="time" value={f.bestTime} onChange={set('bestTime')} /></Field>
              </div>
              <div className="field"><span className="field-label">Photos (optional, up to 4)</span>
                <div className="uploader">
                  {previews.map((p, i) => <div key={p} className="thumb"><img src={p} alt={`Upload ${i + 1}`} /><button type="button" aria-label="Remove photo" onClick={() => setFiles(l => l.filter((_, j) => j !== i))}>✕</button></div>)}
                  {files.length < 4 && <button type="button" className="thumb add" onClick={() => fileInput.current.click()}><span>＋</span>Add</button>}
                  <input ref={fileInput} type="file" accept="image/*" multiple hidden onChange={addFiles} />
                </div>
              </div>
            </>
          )}
          {step === 1 && (
            <>
              <Field label="Pickup address *" error={errors.address}><textarea rows="2" value={f.address} onChange={set('address')} placeholder="Building, street, landmark" /></Field>
              <div className="form-grid">
                <Field label="City *" error={errors.city}><input value={f.city} onChange={set('city')} /></Field>
                <Field label="PIN code *" error={errors.pincode}><input inputMode="numeric" maxLength="6" value={f.pincode} onChange={set('pincode')} /></Field>
              </div>
              <Field label="Contact mobile *" error={errors.phone} hint="Shared with the NGO only after you accept their request"><input inputMode="numeric" maxLength="10" value={f.phone} onChange={set('phone')} /></Field>
            </>
          )}
          {step === 2 && (
            <>
              <fieldset className="field"><legend className="field-label">Handover</legend>
                <div className="radio-row">{['NGO pickup', 'Drop-off'].map(h => <label key={h} className={`radio ${f.handover === h ? 'on' : ''}`}><input type="radio" name="handover" checked={f.handover === h} onChange={() => setF(x => ({ ...x, handover: h }))} />{h === 'NGO pickup' ? 'NGO picks up' : 'I will drop it off'}</label>)}</div>
              </fieldset>
              <div className="form-grid three">
                <Field label="Pickup date *" error={errors.pickupDate}><input type="date" min={today} value={f.pickupDate} onChange={set('pickupDate')} /></Field>
                <Field label="From" error={errors.timeFrom}><input type="time" value={f.timeFrom} onChange={set('timeFrom')} /></Field>
                <Field label="To" error={errors.timeTo}><input type="time" value={f.timeTo} onChange={set('timeTo')} /></Field>
              </div>
              <Field label="Notes for the NGO" error={errors.notes}><textarea rows="3" maxLength="1000" value={f.notes} onChange={set('notes')} placeholder="Gate number, contact person, special handling…" /></Field>
            </>
          )}
          {step === 3 && (
            <dl className="review">
              {[['Food', `${f.foodType} — ${f.quantity} ${f.unit}`], ['Condition', f.condition], ['Best before', `${f.bestDate} ${f.bestTime}`], ['Description', f.description || '—'], ['Address', `${f.address}, ${f.city} ${f.pincode}`], ['Phone', f.phone], ['Handover', f.handover], ['Pickup', `${f.pickupDate}, ${f.timeFrom} – ${f.timeTo}`], ['Notes', f.notes || '—'], ['Photos', `${files.length} attached`]].map(([k, v]) => <div key={k}><dt>{k}</dt><dd>{v}</dd></div>)}
            </dl>
          )}
          <div className="wizard-nav">
            <button className="btn btn-ghost" disabled={step === 0 || busy} onClick={() => setStep(s => s - 1)}>← Back</button>
            {step < 3 ? <button className="btn btn-primary" onClick={next}>Next →</button> : <button className="btn btn-primary" disabled={busy} onClick={submit}>{busy ? 'Submitting…' : '✔ Submit donation'}</button>}
          </div>
        </div>
        <aside className="card tips">
          <h3>Good to know</h3>
          <ul>
            <li>🛡️ Food should be safe, fresh and properly packed.</li>
            <li>🤝 Verified NGOs pick it up — or you can drop it off.</li>
            <li>⭐ You earn reward points based on the quantity donated.</li>
            <li>📍 Your address and phone are shared only after you accept an NGO.</li>
            <li>💾 Your form is saved as a draft on this device.</li>
          </ul>
        </aside>
      </div>
    </>
  );
}
