import { customerMessage } from '../../lib/customerMessage';
import { ReleaseHolds } from '../../components/customer/ReleaseHolds';
import { Navigate, Link, useNavigate } from 'react-router-dom';
import { useSessionStore } from '../../store/sessionStore';
import { HoldTimer } from '../../components/customer/HoldTimer';
import { PriceBreakdown } from '../../components/customer/PriceBreakdown';
import { useEffect, useState } from 'react';
import { apiClient } from '../../lib/api';

export function CheckoutPage() {
  const navigate = useNavigate();
  const [now, setNow] = useState(Date.now());
  useEffect(() => { const timer = setInterval(() => setNow(Date.now()), 1000); return () => clearInterval(timer); }, []);
  const { event, quote, heldSeatIds, holdExpiresAt, pendingConfirmation, holdInProgress, clearHold } = useSessionStore();
  const [error, setError] = useState('');
  const [loading, setLoading] = useState(false);
  const [attempt, setAttempt] = useState(0);
  const key = [...heldSeatIds].sort().join(',');
  useEffect(() => {
    if (!key || pendingConfirmation) return;
    let active = true;
    setLoading(true); setError('');
    apiClient.quote(key.split(',')).then(result => {
      if (active) useSessionStore.setState({ quote: result });
    }, err => { if (active) setError(customerMessage(err, 'We couldn’t update your total. Please try again.')); }).finally(() => { if (active) setLoading(false); });
    return () => { active = false; };
  }, [key, attempt, pendingConfirmation]);
  if (pendingConfirmation) return <Navigate to="/payment" replace />;
  if (!event || !heldSeatIds.length || !holdExpiresAt) return <Navigate to={event ? `/events/${event.id}` : '/'} replace />;
  return <main className="max-w-3xl w-full mx-auto px-4 py-8 space-y-6">
    <h1 className="text-3xl font-bold text-slate-900 dark:text-white">Checkout</h1>
    <p className="text-slate-600 dark:text-slate-400">{event.name} · {event.venueName}, {event.venueCity}</p>
    {loading ? <p role="status">Calculating your price…</p> : error ? <p role="alert">{error}</p> : quote && <PriceBreakdown quote={quote} />}
    {quote && !loading && (quote.validUntil ?? Date.parse(quote.expiresAt)) <= now && <p role="alert">Please review your updated total before continuing.</p>}
    <button onClick={() => setAttempt(attempt + 1)} disabled={loading}>Update total</button>
    <HoldTimer expiresAt={holdExpiresAt} onExpiry={() => { clearHold(); navigate(`/events/${event.id}`, { replace: true }); }} />
    <button onClick={() => { if (holdExpiresAt > Date.now()) navigate('/payment'); }}
      disabled={holdInProgress || loading || !!error || !quote || (quote.validUntil ?? Date.parse(quote.expiresAt)) <= now || holdExpiresAt <= Date.now()} className="w-full px-4 py-3 text-white bg-indigo-600 rounded-lg disabled:opacity-50">Continue to payment</button>
    <ReleaseHolds />
    <Link className="block text-indigo-700 dark:text-indigo-400" to={`/events/${event.id}`}>Back to seat selection</Link>
    <p className="text-xs text-slate-600 dark:text-slate-400">Demo prices. No money will be charged.</p>
  </main>;
}
