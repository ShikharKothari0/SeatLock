import { useEffect, useState, type ReactNode } from 'react';
import { Link, useLocation } from 'react-router-dom';
import { recoverReservation } from '../../lib/reservations';
import { useSessionStore } from '../../store/sessionStore';

export function ReservationRecovery({ children }: { children: ReactNode }) {
  const [state, setState] = useState<'loading' | 'ready' | 'error'>('loading');
  const [attempt, setAttempt] = useState(0);
  const [notice, setNotice] = useState('');
  const location = useLocation();
  const pending = useSessionStore(state => state.pendingConfirmation);
  const verified = useSessionStore(state => state.reservationVerified);
  useEffect(() => {
    let active = true;
    const previousHolds = useSessionStore.getState().heldSeatIds.length;
    recoverReservation().then(response => {
      if (active) {
        setState('ready');
        setNotice(previousHolds && !response.holds.length && !useSessionStore.getState().pendingConfirmation
          ? 'Your previous reservation is no longer active. Check My Bookings if you recently confirmed, or select seats again.' : '');
      }
    }, () => { if (active) setState('error'); });
    return () => { active = false; };
  }, [attempt]);
  const independent = location.pathname.startsWith('/admin') || location.pathname.startsWith('/booking-confirmed') || location.pathname === '/my-bookings';
  // Keep the same child position while confirmation changes state; remounting the payment
  // page here would discard its mutation state before it can navigate to the receipt.
  if (independent || pending || (state === 'ready' && verified)) return <>
    {!independent && !pending && notice && <p role="status" className="p-4 text-amber-800 dark:text-amber-300">{notice}</p>}
    {children}
  </>;
  return <main className="p-8 space-y-4 text-slate-700 dark:text-slate-300">
    {state === 'loading' ? <p role="status">Checking your active reservations…</p> : <>
      <p role="alert">Unable to check your reservation. Please reconnect and try again before selecting seats.</p>
      <button onClick={() => { setState('loading'); setAttempt(attempt + 1); }}>Check reservation again</button>
      <Link className="block text-indigo-600" to="/my-bookings">View bookings</Link>
    </>}
  </main>;
}
