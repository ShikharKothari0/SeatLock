import { ReleaseHolds } from '../../components/customer/ReleaseHolds';
import { recoverReservation } from '../../lib/reservations';
import { Link, useParams, useNavigate } from 'react-router-dom';
import { useQuery } from '@tanstack/react-query';
import { useEffect, useState } from 'react';
import { apiClient } from '../../lib/api';
import { useSessionStore } from '../../store/sessionStore';
import { useSeatHold } from '../../hooks/useSeatHold';
import { SeatGrid } from '../../components/customer/SeatGrid';
import { SelectionSummary } from '../../components/customer/SelectionSummary';
import { HoldTimer } from '../../components/customer/HoldTimer';
import { LoadingSpinner } from '../../components/common/LoadingSpinner';

export function EventPage() {
  const { eventId = '' } = useParams();
  const navigate = useNavigate();
  const session = useSessionStore();
  const [holdError, setHoldError] = useState<string | null>(null);
  const eventQuery = useQuery({ queryKey: ['event', eventId], queryFn: () => apiClient.getEvent(eventId), enabled: !!eventId });
  const seatQuery = useQuery({ queryKey: ['seats', eventId], queryFn: () => apiClient.getSeats(eventId), refetchInterval: 5000, enabled: !!eventId });
  const hold = useSeatHold();
  const { setEvent } = session;
  useEffect(() => {
    if (eventQuery.data && seatQuery.data) setEvent(eventQuery.data, seatQuery.data);
  }, [eventQuery.data, seatQuery.data, setEvent]);

  const holdSelection = async () => {
    const current = useSessionStore.getState();
    if (current.holdInProgress || current.pendingConfirmation || current.event?.id !== eventId) return;
    const ids = [...current.selectedSeatIds];
    if (!ids.length) return;
    current.setHoldInProgress(true);
    setHoldError(null);
    try {
      for (const seatId of ids) {
        await hold.mutateAsync({ seatId, userId: current.userId, eventId });
      }
      navigate('/checkout');
    } catch (err) {
      const error = err as Error & { status?: number };
      setHoldError(error.status === 429 ? 'Too many hold attempts. Wait 10 seconds before trying again.'
        : error.status === 409 ? 'A selected seat is no longer available. Successful holds are kept; choose whether to continue with them.'
        : `${error.message} Checking the server for any holds that succeeded.`);
      try { await recoverReservation(eventId); } catch { useSessionStore.setState({ reservationVerified: false }); setHoldError(previous => `${previous} Reservation recovery failed. Refresh before trying again.`); }
      void seatQuery.refetch();
    } finally {
      useSessionStore.getState().setHoldInProgress(false);
    }
  };

  if (eventQuery.isLoading || seatQuery.isLoading) return <div className="p-12"><LoadingSpinner ariaLabel="Loading event" /></div>;
  const error = eventQuery.error || seatQuery.error;
  if (error) return <main className="p-8 text-slate-700 dark:text-slate-300 space-y-4"><p role="alert">{error.message}</p><button onClick={() => { void eventQuery.refetch(); void seatQuery.refetch(); }}>Retry loading event</button></main>;
  if (!eventQuery.data || !seatQuery.data) return <p className="p-8 text-slate-600 dark:text-slate-400">Event unavailable.</p>;
  if (session.event?.id !== eventId) return <main className="p-8 text-slate-700 dark:text-slate-300 space-y-4">
    <p>Finish your current reservation before selecting seats for another event.</p>
    <Link className="text-indigo-700 dark:text-indigo-400" to={session.pendingConfirmation ? '/payment' : `/events/${session.event?.id ?? eventId}`}>Return to current reservation</Link>
  </main>;
  if (session.pendingConfirmation) return <main className="p-8 text-slate-700 dark:text-slate-300 space-y-4"><p>Your booking confirmation is awaiting a response.</p><Link className="text-indigo-700 dark:text-indigo-400" to="/payment">Resume confirmation</Link></main>;

  const chosen = seatQuery.data.filter(seat => session.selectedSeatIds.includes(seat.id) || session.heldSeatIds.includes(seat.id));
  return <main className="max-w-7xl w-full mx-auto px-4 py-8 space-y-6">
    <h1 className="text-3xl font-bold text-slate-900 dark:text-white">{eventQuery.data.name}: select your seats</h1>
    <p className="text-slate-600 dark:text-slate-400">Seats are held for up to five minutes after you proceed. You can request five holds per ten seconds.</p>
    {holdError && <p role="alert" className="p-3 text-red-700 dark:text-red-400 border border-red-700/50 rounded-lg">{holdError}</p>}
    <div className="grid grid-cols-1 lg:grid-cols-3 gap-6">
      <div className="lg:col-span-2"><SeatGrid eventId={eventId} selectedSeatIds={session.selectedSeatIds} heldSeatIds={session.heldSeatIds}
        disabled={session.holdInProgress}
        onSeatToggle={id => { if (!session.holdInProgress) {
          if (session.selectedSeatIds.includes(id)) session.deselectSeat(id); else session.selectSeat(id);
        } }} /></div>
      <div className="space-y-4">
        <SelectionSummary selectedSeats={chosen} />
        {session.holdExpiresAt && <HoldTimer expiresAt={session.holdExpiresAt} onExpiry={() => { session.clearHold(); setHoldError('Your hold time ended. Please select seats again.'); void seatQuery.refetch(); }} />}
        {session.selectedSeatIds.length > 0 && <button onClick={() => void holdSelection()} disabled={session.holdInProgress}
          className="w-full px-4 py-3 bg-indigo-600 text-white rounded-lg disabled:opacity-50">
          {session.holdInProgress ? 'Holding seats…' : `Hold selected seats (${session.selectedSeatIds.length})`}
        </button>}
        {session.selectedSeatIds.length > 0 && !session.holdInProgress && <button onClick={session.clearSelection} className="text-slate-700 dark:text-slate-300">Clear unheld selection</button>}
        {session.heldSeatIds.length > 0 && <ReleaseHolds />}
        {session.heldSeatIds.length > 0 && <button disabled={session.holdInProgress} onClick={() => { session.clearSelection(); navigate('/checkout'); }}
          className="w-full px-4 py-3 bg-green-600 text-white rounded-lg disabled:opacity-50">Continue with {session.heldSeatIds.length} held seat(s)</button>}
      </div>
    </div>
  </main>;
}
