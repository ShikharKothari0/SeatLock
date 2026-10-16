import { Link, Navigate, useNavigate } from 'react-router-dom';
import { useState } from 'react';
import { useSessionStore } from '../../store/sessionStore';
import { useBookingConfirm } from '../../hooks/useBookingConfirm';
import { HoldTimer } from '../../components/customer/HoldTimer';
import { PRICE_PER_SEAT, PLATFORM_FEE, TAXES } from '../../lib/constants';

export function PaymentPage() {
  const navigate = useNavigate();
  const { event, heldSeatIds, holdExpiresAt, pendingConfirmation, clearHold } = useSessionStore();
  const [method, setMethod] = useState('UPI');
  const [error, setError] = useState<string | null>(null);
  const confirmation = useBookingConfirm({
    onSuccess: booking => navigate(`/booking-confirmed/${booking.id}`, { replace: true }),
    onError: err => setError(err.message),
  });
  if (!pendingConfirmation && (!event || !heldSeatIds.length || !holdExpiresAt)) {
    // Saving the receipt clears the hold before the mutation's navigation callback runs.
    // Do not race that callback with the empty-checkout redirect.
    if (confirmation.isPending || confirmation.isSuccess) return <p className="p-8 text-slate-500" role="status">Opening your booking receipt…</p>;
    return <Navigate to={event ? `/events/${event.id}` : '/'} replace />;
  }
  const total = (pendingConfirmation?.seatIds.length ?? heldSeatIds.length) * PRICE_PER_SEAT + PLATFORM_FEE + TAXES;
  const expired = !pendingConfirmation && (holdExpiresAt ?? 0) <= Date.now();
  return <main className="max-w-3xl w-full mx-auto px-4 py-8 space-y-6">
    <h1 className="text-3xl font-bold text-slate-900 dark:text-white">Demo payment</h1>
    <p className="text-slate-600 dark:text-slate-400">Choose a simulated payment method. No payment is processed and no card details are collected.</p>
    <div className="flex flex-wrap gap-3" role="group" aria-label="Demo payment method">
      {['UPI', 'Card', 'Net banking'].map(option => <button key={option} disabled={confirmation.isPending || !!pendingConfirmation}
        aria-pressed={method === option} onClick={() => setMethod(option)}
        className={`rounded-lg border px-4 py-3 text-slate-900 dark:text-white ${method === option ? 'border-indigo-500 bg-indigo-500/20' : 'border-slate-300 dark:border-slate-600'}`}>{option}</button>)}
    </div>
    <p className="text-xl text-slate-900 dark:text-white">Demo total: ₹{total.toLocaleString('en-IN')}</p>
    {pendingConfirmation && <p role="status" className="text-amber-700 dark:text-amber-400">Confirmation is awaiting a verified response. Retry to check the same booking attempt; do not start a new one.</p>}
    {!pendingConfirmation && holdExpiresAt && <HoldTimer expiresAt={holdExpiresAt} onExpiry={() => {
      clearHold(); navigate(event ? `/events/${event.id}` : '/', { replace: true });
    }} />}
    {error && <p role="alert" className="text-red-700 dark:text-red-400">{error}</p>}
    <button disabled={confirmation.isPending || expired}
      onClick={() => { if (!confirmation.isPending) { setError(null); confirmation.mutate(); } }}
      className="w-full px-4 py-3 text-white bg-indigo-600 rounded-lg disabled:opacity-50">
      {confirmation.isPending ? 'Confirming booking…' : pendingConfirmation ? 'Retry booking confirmation' : 'Confirm demo booking'}
    </button>
    {!pendingConfirmation && <Link to="/checkout" className="block text-indigo-700 dark:text-indigo-400">Back to checkout</Link>}
  </main>;
}
