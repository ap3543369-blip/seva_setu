import { Link } from 'react-router-dom';
import { api } from '../../api.js';
import { useAuth } from '../../auth.jsx';
import { fmt, fmtDateTime, foodImage, useFetch } from '../../utils.js';
import { Async, Avatar, Empty, PageHead, Progress, Stat, StatusBadge } from '../../components/ui.jsx';
import { BarChart } from '../../components/charts.jsx';

export default function DonorDashboard() {
  const { user } = useAuth();
  const dash = useFetch(() => api('/dashboard/donor'));
  const mine = useFetch(() => api('/donations/mine'));
  const top = useFetch(() => api('/impact/leaderboard'));
  const rank = (top.data || []).findIndex(t => t.name === user.name);

  return (
    <>
      <PageHead title={`Welcome, ${user.name}!`} subtitle="Keep donating and earn rewards while making an impact.">
        <Link to="/donor/donate" className="btn btn-primary">➕ Donate food</Link>
      </PageHead>
      <Async state={dash}>{d => (
        <>
          <div className="stat-grid">
            <Stat icon="🍲" label="Total donations" value={fmt(d.donations)} />
            <Stat icon="🍽️" label="Meals donated" value={fmt(d.meals)} tone="orange" />
            <Stat icon="🌍" label="kg CO₂ reduced" value={fmt(d.co2)} tone="blue" />
            <Stat icon="🎁" label="Gift cards earned" value={fmt(d.gifts)} tone="purple" />
            <Stat icon="⭐" label="Reward points" value={fmt(d.points)} tone="pink" />
          </div>
          <div className="two-col wide-left">
            <div className="card">
              <div className="row-between"><h3>Recent donations</h3><Link to="/donor/donations" className="link">View all</Link></div>
              {d.recent.length === 0 ? <Empty title="No donations yet">Your first donation can feed a family today.</Empty> : (
                <ul className="rows">
                  {d.recent.map(r => (
                    <li key={r.id}>
                      <img src={foodImage(r)} alt="" />
                      <div><Link to={`/donation/${r.id}`}><strong>{r.food}</strong></Link> <small>({r.qty})</small><br /><small className="muted">{fmtDateTime(r.createdAt)} · {r.ngo ? `to ${r.ngo}` : 'awaiting NGO'}</small></div>
                      <StatusBadge status={r.status} />
                    </li>
                  ))}
                </ul>
              )}
            </div>
            <div className="stack">
              <div className="card reward-card">
                <h3>Your next reward</h3>
                <div className="reward-big">⭐ {fmt(d.points)} <small>points</small></div>
                <Progress value={d.donations} max={d.milestone} />
                <p className="muted">{d.donations >= d.milestone ? 'Milestone reached — great work!' : `${d.milestone - d.donations} more donation${d.milestone - d.donations > 1 ? 's' : ''} to the next milestone`}</p>
                <Link to="/donor/rewards" className="btn btn-light btn-block">View rewards</Link>
              </div>
              <div className="card rank-card">
                <h3>🏆 Leaderboard</h3>
                <p className="muted">{rank >= 0 ? `You are #${rank + 1} among top donors!` : 'Get a delivery to enter the top donors list.'}</p>
                <ol className="leader mini">{(top.data || []).slice(0, 3).map((t, i) => <li key={t.name} className={t.name === user.name ? 'me' : ''}><span className="rank">{i + 1}</span><Avatar name={t.name} size={30} /><strong>{t.name}</strong><b>{fmt(t.meals)}</b></li>)}</ol>
              </div>
            </div>
          </div>
          {mine.data && mine.data.length > 1 && (
            <div className="card top-gap">
              <h3>Meals per donation <small className="muted">· last {Math.min(8, mine.data.length)}</small></h3>
              <BarChart values={mine.data.slice(0, 8).reverse().map(x => x.meals)} labels={mine.data.slice(0, 8).reverse().map(x => x.food.split(' ')[0])} height={180} />
            </div>
          )}
        </>
      )}</Async>
    </>
  );
}
