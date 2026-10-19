import { useState } from 'react';
import { useQueryClient } from '@tanstack/react-query';
import { apiClient } from '../../lib/api';
import { recoverReservation } from '../../lib/reservations';
import { useSessionStore } from '../../store/sessionStore';

export function ReleaseHolds() {
  const session = useSessionStore();
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState('');
  const queries = useQueryClient();
  const release = async () => {
    setBusy(true); setError(''); session.setHoldInProgress(true);
    try {
      const results = await Promise.allSettled(session.heldSeatIds.map(id => apiClient.releaseHold(id, session.userId)));
      await recoverReservation();
      await queries.invalidateQueries({ queryKey: ['seats'] });
      if (results.some(result => result.status === 'rejected')) setError('Some seats could not be cancelled. Your remaining reservation is shown. Please try again.');
    } catch {
      useSessionStore.setState({ reservationVerified: false });
      setError('We couldn’t check whether your selection was cancelled. Please reconnect and try again.');
    }
    finally { setBusy(false); useSessionStore.getState().setHoldInProgress(false); }
  };
  return <div className="space-y-2">
    <button disabled={busy || session.holdInProgress || !!session.pendingConfirmation} onClick={() => void release()} className="text-indigo-700 dark:text-indigo-400 disabled:opacity-50">
      {busy ? 'Cancelling selection…' : 'Cancel reservation'}
    </button>
    {error && <p role="alert" className="text-red-600">{error}</p>}
  </div>;
}
