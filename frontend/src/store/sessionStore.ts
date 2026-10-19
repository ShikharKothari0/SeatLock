import { create } from 'zustand';
import { createJSONStorage, persist } from 'zustand/middleware';
import type { BookingConfirmRequest, BookingResponse, EventResponse, SeatResponse } from '../types/api';

export interface BookingReceipt {
  booking: BookingResponse;
  event: EventResponse | null;
  seats: SeatResponse[];
}
export interface SessionState {
  quote: import('../types/api').PriceQuote | null;
  reservationVerified: boolean;
  userId: string;
  holdInProgress: boolean;
  setHoldInProgress: (busy: boolean) => void;
  event: EventResponse | null;
  seats: SeatResponse[];
  selectedSeatIds: string[];
  heldSeatIds: string[];
  holdExpiresAt: number | null;
  idempotencyKey: string | null;
  pendingConfirmation: BookingConfirmRequest | null;
  lastBookingId: string | null;
  receipts: BookingReceipt[];
  setEvent: (event: EventResponse, seats: SeatResponse[]) => boolean;
  selectSeat: (seatId: string) => void;
  deselectSeat: (seatId: string) => void;
  clearSelection: () => void;
  setHeld: (seatIds: string[], expiresAt: number) => void;
  clearHold: () => void;
  setBookingConfirmed: (booking: BookingResponse) => void;
  generateIdempotencyKey: () => string;
  beginConfirmation: (method?: import('../types/api').PaymentMethod, scenario?: import('../types/api').PaymentScenario) => BookingConfirmRequest;
  rejectConfirmation: () => void;
  reset: () => void;
}
const checkoutEmpty = {
  quote: null as import('../types/api').PriceQuote | null,
  selectedSeatIds: [] as string[], heldSeatIds: [] as string[],
  holdExpiresAt: null as number | null, idempotencyKey: null as string | null,
  pendingConfirmation: null as BookingConfirmRequest | null,
};
const initialState = {
  reservationVerified: false,
  ...checkoutEmpty, userId: '', holdInProgress: false,
  event: null as EventResponse | null, seats: [] as SeatResponse[],
  lastBookingId: null as string | null, receipts: [] as BookingReceipt[],
};
// Tab-scoped receipts survive refresh; server-backed history is a separate API milestone.
export const useSessionStore = create<SessionState>()(persist((set, get) => ({
  ...initialState,
  setHoldInProgress: holdInProgress => set({ holdInProgress }),
  setEvent: (event, seats) => {
    const state = get();
    const changingEvent = state.event?.id !== event.id;
    if (changingEvent && (state.holdInProgress || state.pendingConfirmation || (state.heldSeatIds.length > 0 && (state.holdExpiresAt ?? 0) > Date.now()))) return false;
    set({ event, seats, ...(changingEvent ? checkoutEmpty : {}) });
    return true;
  },
  selectSeat: seatId => set(state => {
    if (state.pendingConfirmation || state.selectedSeatIds.includes(seatId) || state.heldSeatIds.includes(seatId)) return {};
    return { selectedSeatIds: [...state.selectedSeatIds, seatId], idempotencyKey: null };
  }),
  deselectSeat: seatId => set(state => state.pendingConfirmation ? {} : ({
    selectedSeatIds: state.selectedSeatIds.filter(id => id !== seatId), idempotencyKey: null,
  })),
  clearSelection: () => set(state => state.pendingConfirmation ? {} : { selectedSeatIds: [] }),
  setHeld: (seatIds, expiresAt) => set(state => {
    if (state.pendingConfirmation) return {};
    const heldSeatIds = [...new Set([...state.heldSeatIds, ...seatIds])];
    return { quote: null, heldSeatIds, holdExpiresAt: Math.min(state.holdExpiresAt ?? expiresAt, expiresAt),
      selectedSeatIds: state.selectedSeatIds.filter(id => !heldSeatIds.includes(id)), idempotencyKey: null };
  }),
  clearHold: () => set(state => state.pendingConfirmation ? {} : checkoutEmpty),
  generateIdempotencyKey: () => {
    const key = get().idempotencyKey ?? crypto.randomUUID();
    set({ idempotencyKey: key });
    return key;
  },
  beginConfirmation: (method = 'UPI', scenario = 'SUCCESS') => {
    const state = get();
    // A lost response can hide a committed booking. Retry the original payload and key.
    if (state.pendingConfirmation) return state.pendingConfirmation;
    if (!state.heldSeatIds.length || (state.holdExpiresAt ?? 0) <= Date.now()) throw new Error('Your reservation expired. Please select your seats again.');
    if (!state.quote || (state.quote.validUntil ?? Date.parse(state.quote.expiresAt)) <= Date.now()) throw new Error('Review your updated total at checkout.');
    const request = { method, scenario, userId: state.userId, seatIds: [...state.heldSeatIds], quoteId: state.quote.id, idempotencyKey: state.generateIdempotencyKey() };
    set({ pendingConfirmation: request });
    return request;
  },
  rejectConfirmation: () => set({ pendingConfirmation: null, idempotencyKey: null, quote: null }),
  setBookingConfirmed: booking => set(state => ({
    ...checkoutEmpty, reservationVerified: true, lastBookingId: booking.id,
    receipts: [{ booking, event: state.event?.id === booking.eventId ? state.event : null,
      seats: state.seats.filter(seat => booking.seatIds.includes(seat.id)) },
      ...state.receipts.filter(receipt => receipt.booking.id !== booking.id)].slice(0, 50),
  })),
  reset: () => set(initialState),
}), { name: 'seatlock-session-v1', storage: createJSONStorage(() => sessionStorage),
  partialize: state => ({ ...state, holdInProgress: false, reservationVerified: false }),
}));
