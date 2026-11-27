import { useState } from 'react';
import { api } from '../../api.js';
import { useToast } from '../../toast.jsx';
import { downloadCsv, fmt, monthLabel, useFetch } from '../../utils.js';
import { Empty, Field, PageHead, Spinner } from '../../components/ui.jsx';
import { LineChart } from '../../components/charts.jsx';

const MODEL = { naive: 'Last value', moving_average_3: '3-month average', linear_trend: 'Linear trend', seasonal_naive: 'Same month last year', ridge_trend_seasonal: 'Trend + seasonality (ridge)', gradient_boosting_lags: 'Gradient boosting (lags)' };

export default function EcoForecast() {
  const toast = useToast();
  const cats = useFetch(() => api('/ecoai/categories'));
  const [f, setF] = useState({ horizon: 6, category: '', capacityKg: '' });
  const [res, setRes] = useState(null);
  const [error, setError] = useState(null);
  const [busy, setBusy] = useState(false);
  const set = k => e => setF(x => ({ ...x, [k]: e.target.value }));

  const run = async e => {
    e.preventDefault();
    setBusy(true); setError(null);
    try {
      const p = new URLSearchParams({ horizon: f.horizon });
      if (f.category) p.set('category', f.category);
      if (+f.capacityKg > 0) p.set('capacityKg', f.capacityKg);
      setRes(await api(`/ecoai/forecast?${p}`));
    } catch (ex) { setRes(null); setError(ex); toast(ex.message, 'error'); } finally { setBusy(false); }
  };

  const hist = res?.history || [];
  const fc = res?.forecast || [];
  const values = [...hist.map(h => h.kg), ...fc.map(x => x.kg)];
  const labels = [...hist.map(h => monthLabel(h.month).replace(' 20', " '")), ...fc.map(x => monthLabel(x.month).replace(' 20', " '"))];
  const hasPlan = fc.some(x => x.pickups_needed != null);

  return (
    <>
      <PageHead title="E-waste forecast" subtitle="AI estimate of how much e-waste will arrive in the coming months." />
      <form className="card" onSubmit={run}>
        <div className="form-grid three">
          <Field label="Months ahead"><select value={f.horizon} onChange={set('horizon')}>{[3, 6, 9, 12, 18, 24].map(n => <option key={n} value={n}>{n} months</option>)}</select></Field>
          <Field label="Category"><select value={f.category} onChange={set('category')}><option value="">All categories</option>{(cats.data || []).map(c => <option key={c}>{c}</option>)}</select></Field>
          <Field label="Kg per pickup trip (optional)" hint="Adds a pickups-needed plan"><input type="number" min="1" step="any" value={f.capacityKg} onChange={set('capacityKg')} placeholder="e.g. 250" /></Field>
        </div>
        <button className="btn btn-primary" disabled={busy}>{busy ? 'Forecasting…' : '🔮 Run forecast'}</button>
      </form>
      {busy && <Spinner label="Running the forecast…" />}
      {error && <div className="alert bad top-gap">{error.message}</div>}
      {!res && !busy && !error && <Empty icon="🔮" title="Run a forecast">Pick the options above and press the button.</Empty>}
      {res && (
        <div className="top-gap stack">
          {res.demoData && <div className="alert info">ℹ️ This forecast includes generated demo data, so it is only an example.</div>}
          <div className="card">
            <div className="row-between"><h3>History and forecast · {res.category}</h3><span className={`badge ${res.confidence === 'high' ? 'ok' : res.confidence === 'medium' ? 'info' : 'warn'}`}>{res.confidence} confidence</span></div>
            <LineChart values={values} labels={labels} />
            <p className="muted small">The last {fc.length} points are the forecast. Based on {res.data_points} months of history.</p>
          </div>
          <div className="two-col">
            <div className="card">
              <h3>How it was chosen</h3>
              <p>Model: <strong>{MODEL[res.model] || res.model}</strong></p>
              <p className="small muted">Back-test average error {fmt(res.backtest.mae)} kg per month (a “last value” guess would be off by {fmt(res.backtest.naive_mae)} kg), tested over {res.backtest.origins} past periods up to {res.backtest.months_ahead} months ahead.</p>
              {res.warnings.map(w => <div key={w} className="alert warn">{w}</div>)}
              <p className="small muted">“Confidence” only reflects how much history exists; it is not a guarantee. Ranges are rough 80% bands.</p>
            </div>
            <div className="card">
              <div className="row-between"><h3>Forecast table</h3><button type="button" className="btn btn-sm btn-ghost" onClick={() => downloadCsv('ewaste-forecast.csv', [['Month', 'Forecast kg', 'Low', 'High', ...(hasPlan ? ['Pickups needed', 'Pickups (peak)'] : [])], ...fc.map(x => [x.month, x.kg, x.low, x.high, ...(hasPlan ? [x.pickups_needed, x.pickups_peak] : [])])])}>⬇ CSV</button></div>
              <div className="table-wrap"><table className="table">
                <thead><tr><th>Month</th><th>kg</th><th>Range</th>{hasPlan && <th>Pickups</th>}</tr></thead>
                <tbody>{fc.map(x => <tr key={x.month}><td data-label="Month">{monthLabel(x.month)}</td><td data-label="kg"><strong>{fmt(x.kg)}</strong></td><td data-label="Range">{fmt(x.low)} – {fmt(x.high)}</td>{hasPlan && <td data-label="Pickups">{x.pickups_needed} <small className="muted">(peak {x.pickups_peak})</small></td>}</tr>)}</tbody>
              </table></div>
            </div>
          </div>
        </div>
      )}
    </>
  );
}
