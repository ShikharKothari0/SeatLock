import { apiClient } from './api';
import { localDeadline } from './holdDeadline';
export { localDeadline } from './holdDeadline';
import { useSessionStore } from '../store/sessionStore';

let recoveryVersion = 0;
export async function recoverReservation(eventId?: string) {
  const version = ++recoveryVersion;
  const initial = useSessionStore.getState();
  const started = Date.now();
  const response = await apiClient.getActiveHolds(initial.userId);
  if (initial.pendingConfirmation || useSessionStore.getState().pendingConfirmation) return response;
  const preferred = eventId ?? initial.event?.id;
  const first = response.holds.find(hold => hold.event.id === preferred) ?? response.holds[0];
  const holds = first ? response.holds.filter(hold => hold.event.id === first.event.id) : [];
  const seats = first ? await apiClient.getSeats(first.event.id) : initial.seats;
  const state = useSessionStore.getState();
  if (version !== recoveryVersion || state.pendingConfirmation || state.userId !== initial.userId) return response;
  useSessionStore.setState({
    reservationVerified: true,
    event: first?.event ?? state.event, seats,
    heldSeatIds: holds.map(hold => hold.seatId),
    holdExpiresAt: holds.length ? Math.min(...holds.map(hold => localDeadline(hold, started))) : null,
    selectedSeatIds: state.event?.id === (first?.event.id ?? state.event?.id)
      ? state.selectedSeatIds.filter(id => !holds.some(hold => hold.seatId === id)) : [],
    idempotencyKey: null,
  });
  return response;
}
