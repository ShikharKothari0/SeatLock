import { ReleaseHolds } from '../../components/customer/ReleaseHolds';
import { Navigate, Link, useNavigate } from 'react-router-dom';
import { useSessionStore } from '../../store/sessionStore';
import { HoldTimer } from '../../components/customer/HoldTimer';
import { SelectionSummary } from '../../components/customer/SelectionSummary';

export function CheckoutPage() {
  const navigate = useNavigate();
  const { event, seats, heldSeatIds, holdExpiresAt, pendingConfirmation, holdInProgress, clearHold } = useSessionStore();
  if (pendingConfirmation) return <Navigate to="/payment" replace />;
  if (!event || !heldSeatIds.length || !holdExpiresAt) return <Navigate to={event ? `/events/${event.id}` : '/'} replace />;
  const heldSeats = seats.filter(seat => heldSeatIds.includes(seat.id));
  return <main className="max-w-3xl w-full mx-auto px-4 py-8 space-y-6">
    <h1 className="text-3xl font-bold text-slate-900 dark:text-white">Checkout</h1>
    <p className="text-slate-600 dark:text-slate-400">{event.name} · {event.venueName}, {event.venueCity}</p>
    <SelectionSummary selectedSeats={heldSeats} />
    <HoldTimer expiresAt={holdExpiresAt} onExpiry={() => { clearHold(); navigate(`/events/${event.id}`, { replace: true }); }} />
    <button onClick={() => { if (holdExpiresAt > Date.now()) navigate('/payment'); }}
      disabled={holdInProgress || holdExpiresAt <= Date.now()} className="w-full px-4 py-3 text-white bg-indigo-600 rounded-lg disabled:opacity-50">Proceed to Payment</button>
    <ReleaseHolds />
    <Link className="block text-indigo-700 dark:text-indigo-400" to={`/events/${event.id}`}>Back to seat selection</Link>
    <p className="text-xs text-slate-600 dark:text-slate-400">Demo prices. No money will be charged.</p>
  </main>;
}
