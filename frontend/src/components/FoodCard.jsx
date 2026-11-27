import { Link } from 'react-router-dom';
import { foodImage, fmtPickup } from '../utils.js';
import { StatusBadge } from './ui.jsx';

export default function FoodCard({ d, action, compact }) {
  return (
    <article className={`food-card ${compact ? 'compact' : ''}`}>
      <Link to={`/donation/${d.id}`} className="food-img">
        <img src={foodImage(d)} alt={d.food} loading="lazy" />
        <span className="chip">{d.category}</span>
        {d.status && d.status !== 'Pending' && <span className="food-status"><StatusBadge status={d.status} /></span>}
      </Link>
      <div className="food-body">
        <h3><Link to={`/donation/${d.id}`}>{d.food}</Link> <small>({d.qty})</small></h3>
        <p className="muted">{d.donor} · {d.area}</p>
        <div className="food-meta">
          <span title="Approximate distance">📍 {d.distanceKm} km</span>
          <span title="Pickup time">🕒 {fmtPickup(d.pickupAt)}</span>
          <span title="Meals">🍛 {d.meals} meals</span>
        </div>
        {action && <div className="food-actions">{action}</div>}
      </div>
    </article>
  );
}
