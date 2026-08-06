// Session Store - Zustand store for user session, seat selection, holds, and booking state

import { create } from 'zustand';

export interface SessionState {
  // User identification (hardcoded for demo)
  userId: string;

  // Seat selection state
  selectedSeatIds: string[];

  // Confirmed hold state
  heldSeatIds: string[];
  holdExpiresAt: number | null; // Unix timestamp in ms

  // Idempotency key for booking confirmation
  idempotencyKey: string | null;

  // Last confirmed booking
  lastBookingId: string | null;

  // Actions
  selectSeat: (seatId: string) => void;
  deselectSeat: (seatId: string) => void;
  clearSelection: () => void;
  setHeld: (seatIds: string[], expiresAt: number) => void;
  clearHold: () => void;
  setBookingConfirmed: (bookingId: string) => void;
  generateIdempotencyKey: () => void;
  clearIdempotencyKey: () => void;
  reset: () => void;
}

const initialState = {
  userId: '33333333-3333-3333-3333-333333333333',
  selectedSeatIds: [] as string[],
  heldSeatIds: [] as string[],
  holdExpiresAt: null as number | null,
  idempotencyKey: null as string | null,
  lastBookingId: null as string | null,
};

export const useSessionStore = create<SessionState>((set) => ({
  ...initialState,

  selectSeat: (seatId: string) =>
    set((state) => {
      if (state.selectedSeatIds.includes(seatId)) return state;
      return { selectedSeatIds: [...state.selectedSeatIds, seatId] };
    }),

  deselectSeat: (seatId: string) =>
    set((state) => ({
      selectedSeatIds: state.selectedSeatIds.filter((id) => id !== seatId),
    })),

  clearSelection: () =>
    set({ selectedSeatIds: [] }),

  setHeld: (seatIds: string[], expiresAt: number) =>
    set({
      heldSeatIds: seatIds,
      holdExpiresAt: expiresAt,
      selectedSeatIds: [], // Clear selection after hold
    }),

  clearHold: () =>
    set({
      heldSeatIds: [],
      holdExpiresAt: null,
    }),

  setBookingConfirmed: (bookingId: string) =>
    set({
      lastBookingId: bookingId,
      heldSeatIds: [],
      holdExpiresAt: null,
      idempotencyKey: null,
    }),

  generateIdempotencyKey: () =>
    set({ idempotencyKey: crypto.randomUUID() }),

  clearIdempotencyKey: () =>
    set({ idempotencyKey: null }),

  reset: () => set(initialState),
}));