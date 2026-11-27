import { useState } from 'react';
import { useNavigate } from 'react-router-dom';
import { api, post } from '../../api.js';
import { useToast } from '../../toast.jsx';
import { Async, Field, PageHead } from '../../components/ui.jsx';
import { useFetch } from '../../utils.js';

const EMPTY = { category: '', medicineName: '', strength: '', dosageForm: '', batchNumber: '', expiryDate: '', quantity: '', unit: 'strips', sealedOriginalPack: true, storage: 'Room temperature', licenceNumber: '', notes: '', address: '', city: '', phone: '', declareAuthorised: false, declareNotControlled: false, declareUnexpiredAndSafe: false };

export default function MediDonate() {
  const toast = useToast();
  const nav = useNavigate();
  const policy = useFetch(() => api('/medishare/policy'));
  const [f, setF] = useState(EMPTY);
  const [errors, setErrors] = useState({});
  const [busy, setBusy] = useState(false);
  const set = k => e => { setF(x => ({ ...x, [k]: e.target.type === 'checkbox' ? e.target.checked : e.target.value })); setErrors(x => ({ ...x, [k]: undefined })); };

  const validate = p => {
    const e = {};
    if (!f.category) e.category = 'Choose a category.';
    if (!f.medicineName.trim()) e.medicineName = 'Enter the medicine / item name.';
    if (!f.batchNumber.trim()) e.batchNumber = 'Batch number is required.';
    if (!f.expiryDate) e.expiryDate = 'Expiry date is required.';
    else if ((new Date(f.expiryDate) - new Date()) / 86400000 < p.minShelfLifeDays) e.expiryDate = `Needs at least ${p.minShelfLifeDays} days of shelf life left.`;
    if (!(+f.quantity >= 1)) e.quantity = 'Enter a quantity of at least 1.';
    if (!f.unit.trim()) e.unit = 'Enter a unit.';
    if (p.requireSealedPack && !f.sealedOriginalPack) e.sealedOriginalPack = 'Only sealed, original-pack items can be listed.';
    if (f.storage.startsWith('Refrigerated') && !p.allowColdChain) e.storage = 'Cold-chain items are not accepted.';
    if (f.licenceNumber.trim().length < 3) e.licenceNumber = 'Your licence / registration number is required.';
    if (!f.address.trim()) e.address = 'Pickup address is required.';
    if (!f.city.trim()) e.city = 'City is required.';
    if (!/^[6-9]\d{9}$/.test(f.phone)) e.phone = 'Enter a valid 10-digit mobile number.';
    if (!f.declareAuthorised) e.declareAuthorised = 'Please confirm.';
    if (!f.declareNotControlled) e.declareNotControlled = 'Please confirm.';
    if (!f.declareUnexpiredAndSafe) e.declareUnexpiredAndSafe = 'Please confirm.';
    setErrors(e);
    return Object.keys(e).length === 0;
  };

  const submit = async (ev, p) => {
    ev.preventDefault();
    if (!validate(p)) return;
    setBusy(true);
    try {
      const d = await post('/medishare/donations', { ...f, quantity: +f.quantity });
      toast(d.status === 'PENDING_REVIEW' ? 'Submitted. An admin will review it before it goes live.' : 'Listed. Verified NGOs can now request it.');
      nav('/medishare/mine');
    } catch (ex) { setErrors(ex.errors || {}); toast(ex.message, 'error'); } finally { setBusy(false); }
  };

  return (
    <>
      <PageHead title="Donate medicines & supplies" subtitle="For authorised hospitals and pharmacies only." />
      <Async state={policy}>{p => (
        <form className="card" onSubmit={ev => submit(ev, p)} noValidate>
          <div className="alert warn"><span>⚠️ {p.notice}</span></div>
          <div className="form-grid">
            <Field label="Category *" error={errors.category}><select value={f.category} onChange={set('category')}><option value="">Choose…</option>{p.allowedCategories.map(c => <option key={c}>{c}</option>)}</select></Field>
            <Field label="Medicine / item name *" error={errors.medicineName}><input value={f.medicineName} onChange={set('medicineName')} maxLength="150" /></Field>
            <Field label="Strength" error={errors.strength}><input value={f.strength} onChange={set('strength')} placeholder="e.g. 500 mg" /></Field>
            <Field label="Form" error={errors.dosageForm}><input value={f.dosageForm} onChange={set('dosageForm')} placeholder="Tablet, syrup, box…" /></Field>
            <Field label="Batch number *" error={errors.batchNumber}><input value={f.batchNumber} onChange={set('batchNumber')} /></Field>
            <Field label="Expiry date *" error={errors.expiryDate} hint={`At least ${p.minShelfLifeDays} days from today`}><input type="date" value={f.expiryDate} onChange={set('expiryDate')} /></Field>
            <Field label="Quantity *" error={errors.quantity}><input type="number" min="1" value={f.quantity} onChange={set('quantity')} /></Field>
            <Field label="Unit *" error={errors.unit}><input value={f.unit} onChange={set('unit')} placeholder="strips, bottles, boxes" /></Field>
            <Field label="Storage *" error={errors.storage}><select value={f.storage} onChange={set('storage')}>{p.storageOptions.map(s => <option key={s}>{s}</option>)}</select></Field>
            <Field label="Your licence / registration no. *" error={errors.licenceNumber} hint="Visible to you and admins only"><input value={f.licenceNumber} onChange={set('licenceNumber')} /></Field>
          </div>
          <label className={`check ${errors.sealedOriginalPack ? 'has-error' : ''}`}><input type="checkbox" checked={f.sealedOriginalPack} onChange={set('sealedOriginalPack')} /> Sealed, original manufacturer packaging</label>
          {errors.sealedOriginalPack && <small className="field-error">{errors.sealedOriginalPack}</small>}
          <Field label="Notes" error={errors.notes}><textarea rows="2" maxLength="1000" value={f.notes} onChange={set('notes')} /></Field>
          <div className="form-grid">
            <Field label="Pickup address *" error={errors.address} hint="Shown only to an approved requester"><textarea rows="2" value={f.address} onChange={set('address')} /></Field>
            <div>
              <Field label="City *" error={errors.city}><input value={f.city} onChange={set('city')} /></Field>
              <Field label="Contact mobile *" error={errors.phone}><input inputMode="numeric" maxLength="10" value={f.phone} onChange={set('phone')} /></Field>
            </div>
          </div>
          <fieldset className="declarations"><legend className="field-label">Declarations *</legend>
            {[['declareAuthorised', 'I am authorised to donate these items on behalf of my organisation.'], ['declareNotControlled', 'None of these items is a controlled, narcotic or psychotropic substance.'], ['declareUnexpiredAndSafe', 'The items are unexpired, undamaged and have been stored correctly.']].map(([k, t]) => (
              <div key={k}><label className="check"><input type="checkbox" checked={f[k]} onChange={set(k)} /> {t}</label>{errors[k] && <small className="field-error">{errors[k]}</small>}</div>
            ))}
          </fieldset>
          <button className="btn btn-primary" disabled={busy}>{busy ? 'Submitting…' : p.requireAdminReview ? 'Submit for review' : 'List items'}</button>
        </form>
      )}</Async>
    </>
  );
}
