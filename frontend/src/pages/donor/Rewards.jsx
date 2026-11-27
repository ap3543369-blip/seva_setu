import { useState } from 'react';
import { api, post } from '../../api.js';
import { useAuth } from '../../auth.jsx';
import { useToast } from '../../toast.jsx';
import { fmt, fmtDate, useFetch } from '../../utils.js';
import { Async, Empty, Modal, PageHead, Progress, Tabs } from '../../components/ui.jsx';
import TiltCard from '../../components/TiltCard.jsx';

export default function Rewards() {
  const { setUser } = useAuth();
  const toast = useToast();
  const offers = useFetch(() => api('/rewards'));
  const mine = useFetch(() => api('/rewards/mine'));
  const [tab, setTab] = useState('available');
  const [pick, setPick] = useState(null);
  const [won, setWon] = useState(null);
  const [busy, setBusy] = useState(false);

  const redeem = async () => {
    setBusy(true);
    try {
      const r = await post(`/rewards/${pick.id}/redeem`);
      setUser(u => ({ ...u, points: r.pointsLeft }));
      setPick(null); setWon(r.reward);
      offers.reload(); mine.reload();
    } catch (e) { toast(e.message, 'error'); setPick(null); } finally { setBusy(false); }
  };
  const copy = code => { navigator.clipboard?.writeText(code); toast('Code copied!'); };

  return (
    <>
      <PageHead title="Rewards" subtitle="Donate more food and earn exciting gift cards.">
        {offers.data && <div className="points-chip big">⭐ {fmt(offers.data.points)} points</div>}
      </PageHead>
      <Tabs value={tab} onChange={setTab} tabs={[{ key: 'available', label: 'Available rewards' }, { key: 'mine', label: 'My rewards', count: mine.data?.length }]} />
      {tab === 'available' ? (
        <Async state={offers}>{d => {
          const next = d.offers.filter(o => o.cost > d.points).sort((a, b) => a.cost - b.cost)[0];
          return (
            <>
              {next && <div className="card next-reward"><span>Next up: <strong>₹{next.amount} {next.brand}</strong></span><Progress value={d.points} max={next.cost} /><small className="muted">{fmt(next.cost - d.points)} more points needed</small></div>}
              <div className="grid-3">
                {d.offers.map(o => {
                  const can = d.points >= o.cost;
                  return (
                    <TiltCard key={o.id} className={`card gift-card ${can ? '' : 'locked'}`}>
                      <img src={`/assets/images/rewards/${o.logo}.svg`} alt={o.brand} />
                      <strong>₹{fmt(o.amount)}</strong>
                      <small className="muted">{o.brand} Gift Card</small>
                      <span className="points-chip">⭐ {fmt(o.cost)} pts</span>
                      <button className="btn btn-primary btn-block" disabled={!can} onClick={() => setPick(o)}>{can ? 'Redeem' : `Need ${fmt(o.cost - d.points)} more`}</button>
                    </TiltCard>
                  );
                })}
              </div>
            </>
          );
        }}</Async>
      ) : (
        <Async state={mine}>{rows => rows.length === 0 ? <Empty icon="🎁" title="No rewards redeemed yet">Redeem points to see your gift-card codes here.</Empty> : (
          <div className="grid-3">
            {[...rows].reverse().map(r => (
              <div className="card gift-card" key={r.id}>
                <img src={`/assets/images/rewards/${r.logo}.svg`} alt={r.brand} />
                <strong>₹{fmt(r.amount)} {r.brand}</strong>
                <small className="muted">Redeemed {fmtDate(r.redeemedAt)} · {fmt(r.cost)} pts</small>
                <code className="code">{r.code}</code>
                <button className="btn btn-ghost btn-block" onClick={() => copy(r.code)}>Copy code</button>
              </div>
            ))}
          </div>
        )}</Async>
      )}
      {pick && (
        <Modal title="Confirm redemption" onClose={() => setPick(null)} footer={<><button className="btn btn-ghost" onClick={() => setPick(null)}>Cancel</button><button className="btn btn-primary" disabled={busy} onClick={redeem}>{busy ? 'Redeeming…' : 'Redeem'}</button></>}>
          <p>Redeem <strong>₹{fmt(pick.amount)} {pick.brand}</strong> gift card for <strong>{fmt(pick.cost)} points</strong>?</p>
        </Modal>
      )}
      {won && (
        <Modal title="🎉 Reward unlocked!" onClose={() => setWon(null)} footer={<button className="btn btn-primary" onClick={() => setWon(null)}>Done</button>}>
          <p>Your ₹{fmt(won.amount)} {won.brand} gift card is ready. Use this code:</p>
          <code className="code big">{won.code}</code>
          <button className="btn btn-ghost" onClick={() => copy(won.code)}>Copy code</button>
        </Modal>
      )}
    </>
  );
}
