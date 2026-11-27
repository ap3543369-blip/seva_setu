import { useState } from 'react';
import { fmt } from '../utils.js';

/** Simple responsive bar chart. values: number[], labels: string[] */
export function BarChart({ values, labels, color = 'var(--green)', height = 200, unit = '' }) {
  const max = Math.max(1, ...values);
  const top = Math.ceil(max / 4) * 4 || 4;
  return (
    <div className="bars" style={{ height }} role="img" aria-label={`Bar chart: ${values.map((v, i) => `${labels[i]} ${v}`).join(', ')}`}>
      <div className="bars-grid">
        {[1, 0.75, 0.5, 0.25, 0].map(f => <span key={f}><i>{fmt(Math.round(top * f))}</i></span>)}
      </div>
      <div className="bars-cols">
        {values.map((v, i) => (
          <div className="bars-col" key={i} title={`${labels[i]}: ${fmt(v)}${unit}`}>
            <b style={{ height: `${(v / top) * 100}%`, background: color }}><em>{fmt(v)}</em></b>
            <span>{labels[i]}</span>
          </div>
        ))}
      </div>
    </div>
  );
}

/** Smooth area/line chart in SVG with hover dots. */
export function LineChart({ values, labels, height = 240 }) {
  const [hover, setHover] = useState(null);
  const W = 640, H = height, P = { l: 44, r: 16, t: 16, b: 28 };
  const max = Math.max(1, ...values);
  const top = Math.ceil(max / 4 / 10) * 10 * 4 || 4;
  const x = i => P.l + (values.length === 1 ? (W - P.l - P.r) / 2 : (i / (values.length - 1)) * (W - P.l - P.r));
  const y = v => P.t + (1 - v / top) * (H - P.t - P.b);
  const line = values.map((v, i) => `${i ? 'L' : 'M'}${x(i).toFixed(1)},${y(v).toFixed(1)}`).join(' ');
  const area = `${line} L${x(values.length - 1)},${H - P.b} L${x(0)},${H - P.b} Z`;
  return (
    <svg className="linechart" viewBox={`0 0 ${W} ${H}`} role="img" aria-label="Line chart">
      <defs>
        <linearGradient id="lc-fill" x1="0" x2="0" y1="0" y2="1">
          <stop offset="0" stopColor="#168A4A" stopOpacity=".35" />
          <stop offset="1" stopColor="#168A4A" stopOpacity="0" />
        </linearGradient>
      </defs>
      {[0, 0.25, 0.5, 0.75, 1].map(f => (
        <g key={f}>
          <line x1={P.l} x2={W - P.r} y1={y(top * f)} y2={y(top * f)} className="lc-grid" />
          <text x={P.l - 8} y={y(top * f) + 4} textAnchor="end" className="lc-axis">{fmt(Math.round(top * f))}</text>
        </g>
      ))}
      <path d={area} fill="url(#lc-fill)" />
      <path d={line} className="lc-line" />
      {values.map((v, i) => (
        <g key={i} onMouseEnter={() => setHover(i)} onMouseLeave={() => setHover(null)}>
          <circle cx={x(i)} cy={y(v)} r={hover === i ? 6 : 4} className="lc-dot" />
          <rect x={x(i) - 18} y={P.t} width="36" height={H - P.t - P.b} fill="transparent" />
          <text x={x(i)} y={H - 8} textAnchor="middle" className="lc-axis">{labels[i]}</text>
          {hover === i && (
            <g>
              <rect x={x(i) - 36} y={y(v) - 34} width="72" height="22" rx="6" className="lc-tip" />
              <text x={x(i)} y={y(v) - 19} textAnchor="middle" className="lc-tip-text">{fmt(v)}</text>
            </g>
          )}
        </g>
      ))}
    </svg>
  );
}

/** Donut chart. segments: [{label, value, color}] */
export function Donut({ segments, size = 160, center }) {
  const total = segments.reduce((s, x) => s + x.value, 0) || 1;
  const r = 52, c = 2 * Math.PI * r;
  let offset = 0;
  return (
    <div className="donut-wrap">
      <svg width={size} height={size} viewBox="0 0 140 140" role="img" aria-label="Donut chart">
        <circle cx="70" cy="70" r={r} fill="none" stroke="var(--line)" strokeWidth="18" />
        {segments.filter(s => s.value > 0).map(s => {
          const len = (s.value / total) * c;
          const el = <circle key={s.label} cx="70" cy="70" r={r} fill="none" stroke={s.color} strokeWidth="18" strokeDasharray={`${len} ${c - len}`} strokeDashoffset={-offset} transform="rotate(-90 70 70)" />;
          offset += len;
          return el;
        })}
        <text x="70" y="68" textAnchor="middle" className="donut-num">{center ?? total}</text>
        <text x="70" y="84" textAnchor="middle" className="donut-sub">total</text>
      </svg>
      <ul className="legend">
        {segments.map(s => <li key={s.label}><i style={{ background: s.color }} />{s.label}<b>{s.value}</b></li>)}
      </ul>
    </div>
  );
}
