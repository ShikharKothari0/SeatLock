import { test, beforeEach } from 'node:test';
import assert from 'node:assert/strict';
const data = new Map<string, string>();
Object.defineProperty(globalThis, 'sessionStorage', { value: {
  getItem: (key: string) => data.get(key) ?? null,
  setItem: (key: string, value: string) => data.set(key, value),
  removeItem: (key: string) => data.delete(key),
}, configurable: true });
const { useSessionStore: store } = await import('../src/store/sessionStore.ts');
const event = { id: 'event-1', name: 'Concert', venueName: 'Arena', venueCity: 'Mumbai', saleStartTime: '2026-01-01T00:00:00Z', eventTime: '2026-12-01T00:00:00Z' };
const seats = ['seat-1', 'seat-2'].map((id, i) => ({ id, seatNumber: `A${i + 1}`, section: 'A', status: 'AVAILABLE' as const }));
const booking = { id: 'booking-1', userId: 'user', eventId: event.id, status: 'CONFIRMED' as const, createdAt: '2026-10-16T00:00:00Z', seatIds: seats.map(s => s.id) };
beforeEach(() => { store.getState().reset(); store.getState().setEvent(event, seats); });

test('partial success retains remaining selection and earliest hold deadline', () => {
  const state = store.getState();
  state.selectSeat('seat-1'); state.selectSeat('seat-2');
  const first = Date.now() + 60_000;
  state.setHeld(['seat-1'], first);
  assert.deepEqual(store.getState().selectedSeatIds, ['seat-2']);
  state.setHeld(['seat-2', 'seat-1'], first + 30_000);
  assert.deepEqual(store.getState().heldSeatIds, ['seat-1', 'seat-2']);
  assert.equal(store.getState().holdExpiresAt, first);
});
test('cannot mix event seats during holds or in-flight hold requests', () => {
  store.getState().setHoldInProgress(true);
  assert.equal(store.getState().setEvent({ ...event, id: 'event-2' }, []), false);
  store.getState().setHoldInProgress(false);
  store.getState().setHeld(['seat-1'], Date.now() + 60_000);
  assert.equal(store.getState().setEvent({ ...event, id: 'event-2' }, []), false);
});
test('expired holds do not prevent switching events', () => {
  store.getState().setHeld(['seat-1'], Date.now() - 1);
  assert.equal(store.getState().setEvent({ ...event, id: 'event-2' }, []), true);
  assert.deepEqual(store.getState().heldSeatIds, []);
});
test('fresh confirmation is rejected when the local hold has expired', () => {
  store.getState().setHeld(['seat-1'], Date.now() - 1);
  assert.throws(() => store.getState().beginConfirmation(), /expired/);
});
test('ambiguous confirmation preserves payload and key even after expiry and refresh', async () => {
  store.getState().setHeld(['seat-1'], Date.now() + 60_000);
  const first = store.getState().beginConfirmation();
  store.setState({ holdExpiresAt: Date.now() - 1 });
  store.getState().clearHold();
  store.getState().selectSeat('seat-2');
  store.getState().setHeld(['seat-2'], Date.now() + 60_000);
  await store.persist.rehydrate();
  assert.deepEqual(store.getState().beginConfirmation(), first);
  assert.deepEqual(store.getState().heldSeatIds, ['seat-1']);
  assert.deepEqual(store.getState().selectedSeatIds, []);
});
test('server response is retained with real seat and event details', async () => {
  store.getState().setBookingConfirmed(booking);
  await store.persist.rehydrate();
  const receipt = store.getState().receipts[0];
  assert.deepEqual(receipt.booking, booking);
  assert.deepEqual(receipt.seats, seats);
  assert.equal(receipt.event?.name, 'Concert');
  assert.equal(store.getState().pendingConfirmation, null);
});
test('repeated successful responses do not duplicate history', () => {
  store.getState().setBookingConfirmed(booking);
  store.getState().setBookingConfirmed(booking);
  assert.equal(store.getState().receipts.length, 1);
});
test('idempotency key is stable until the reservation changes', () => {
  store.getState().setHeld(['seat-1'], Date.now() + 60_000);
  const first = store.getState().generateIdempotencyKey();
  assert.equal(store.getState().generateIdempotencyKey(), first);
  store.getState().setHeld(['seat-2'], Date.now() + 60_000);
  assert.notEqual(store.getState().generateIdempotencyKey(), first);
});
test('in-flight UI flags are not restored after refresh', async () => {
  store.getState().setHoldInProgress(true);
  await store.persist.rehydrate();
  assert.equal(store.getState().holdInProgress, false);
});
