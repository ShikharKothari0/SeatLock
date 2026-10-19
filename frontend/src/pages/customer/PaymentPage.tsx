import type { PaymentMethod, PaymentScenario } from '../../types/api';
import { customerMessage } from '../../lib/customerMessage';
import { Link, Navigate, useNavigate } from 'react-router-dom';
import { useState, useEffect } from 'react';
import { useSessionStore } from '../../store/sessionStore';
import { useBookingConfirm } from '../../hooks/useBookingConfirm';
import { HoldTimer } from '../../components/customer/HoldTimer';
import { PriceBreakdown } from '../../components/customer/PriceBreakdown';

export function PaymentPage() {
  const navigate = useNavigate();
  const { event, quote, heldSeatIds, holdExpiresAt, pendingConfirmation, clearHold } = useSessionStore();
  const [now, setNow] = useState(Date.now());
  useEffect(() => { const timer = setInterval(() => setNow(Date.now()), 1000); return () => clearInterval(timer); }, []);
  const [method, setMethod] = useState<PaymentMethod>('UPI');
  const [scenario, setScenario] = useState<PaymentScenario>('SUCCESS');
  const [error, setError] = useState<string | null>(null);
  const confirmation = useBookingConfirm({
    onSuccess: booking => navigate(`/booking-confirmed/${booking.id}`, { replace: true }),
    onFailed: attempt => setError(attempt.status === 'EXPIRED'
      ? 'Your reservation expired before payment completed. Please select your seats again.'
      : attempt.resultCode === 'SIMULATED_DECLINE'
        ? 'Demo payment was declined. No money was charged. Return to checkout to try again.'
        : 'Your reservation could not be completed. No money was charged. Return to checkout to review your seats.'),
    onError: err => setError(customerMessage(err, 'We couldn’t confirm your booking status. Please check again before starting another booking.')),
  });
  const { mutate, isPending, isError } = confirmation;
  useEffect(() => {
    if (!pendingConfirmation || isPending || isError) return;
    const timer = window.setTimeout(() => mutate({ method, scenario }), 2000);
    return () => window.clearTimeout(timer);
  }, [pendingConfirmation, isPending, isError, mutate, method, scenario]);
  if (!pendingConfirmation && (!event || !heldSeatIds.length || !holdExpiresAt)) {
    // Saving the receipt clears the hold before the mutation's navigation callback runs.
    // Do not race that callback with the empty-checkout redirect.
    if (confirmation.isPending || confirmation.data?.status === 'SUCCEEDED') return <p className="p-8 text-slate-500" role="status">Opening your booking receipt…</p>;
    return <Navigate to={event ? `/events/${event.id}` : '/'} replace />;
  }
  const selectedMethod = pendingConfirmation?.method ?? method;
  const selectedScenario = pendingConfirmation?.scenario ?? scenario;
  const expired = !pendingConfirmation && ((holdExpiresAt ?? 0) <= Date.now() || !quote || (quote.validUntil ?? Date.parse(quote.expiresAt)) <= now);
  return <main className="max-w-3xl w-full mx-auto px-4 py-8 space-y-6">
    <h1 className="text-3xl font-bold text-slate-900 dark:text-white">Demo payment</h1>
    <p className="text-slate-600 dark:text-slate-400">Choose a simulated payment method. No payment is processed and no card details are collected.</p>
    <div className="flex flex-wrap gap-3" role="group" aria-label="Demo payment method">
      {(['UPI', 'CARD', 'NET_BANKING'] as const).map(option => <button key={option} disabled={confirmation.isPending || !!pendingConfirmation}
        aria-pressed={selectedMethod === option} onClick={() => setMethod(option)}
        className={`rounded-lg border px-4 py-3 text-slate-900 dark:text-white ${selectedMethod === option ? 'border-indigo-500 bg-indigo-500/20' : 'border-slate-300 dark:border-slate-600'}`}>{option === 'CARD' ? 'Card' : option === 'NET_BANKING' ? 'Net banking' : 'UPI'}</button>)}
    </div>
    <label className="block text-slate-700 dark:text-slate-300">Try a demo payment outcome
      <select aria-label="Demo payment outcome" disabled={confirmation.isPending || !!pendingConfirmation}
        value={selectedScenario} onChange={e => setScenario(e.target.value as PaymentScenario)}
        className="block mt-2 border rounded p-2 bg-white dark:bg-slate-900">
        <option value="SUCCESS">Successful payment</option>
        <option value="FAILURE">Declined payment</option>
        <option value="DELAYED_SUCCESS">Delayed payment (about 15 seconds)</option>
      </select>
    </label>
    {quote && <PriceBreakdown quote={quote} />}
    {pendingConfirmation && <p role="status" className="text-amber-700 dark:text-amber-400">Your demo payment is being checked. You can refresh this page safely. Please wait before starting another booking.</p>}
    {!pendingConfirmation && holdExpiresAt && <HoldTimer expiresAt={holdExpiresAt} onExpiry={() => {
      clearHold(); navigate(event ? `/events/${event.id}` : '/', { replace: true });
    }} />}
    {!pendingConfirmation && quote && <p>This total is available until {new Date(quote.expiresAt).toLocaleTimeString()}. {expired && 'Return to checkout to review your updated total.'}</p>}
    {error && <p role="alert" className="text-red-700 dark:text-red-400">{error}</p>}
    <button disabled={confirmation.isPending || expired}
      onClick={() => { if (!confirmation.isPending) { setError(null); confirmation.mutate({ method, scenario }); } }}
      className="w-full px-4 py-3 text-white bg-indigo-600 rounded-lg disabled:opacity-50">
      {confirmation.isPending ? 'Confirming booking…' : pendingConfirmation ? 'Check booking status' : 'Confirm demo booking'}
    </button>
    {!pendingConfirmation && <Link to="/checkout" className="block text-indigo-700 dark:text-indigo-400">Back to checkout</Link>}
  </main>;
}
