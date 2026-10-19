// API Types - Mirror exactly from FRONTEND-INTEGRATION-GUIDE.md Part 2.1

// All UUID fields are strings in ISO 8601 UUID format.
// All timestamps are ISO 8601 strings in UTC.

export type SeatStatus = 'AVAILABLE' | 'HELD' | 'CONFIRMED';

export type BookingStatus = 'HELD' | 'CONFIRMED' | 'EXPIRED' | 'CANCELLED';

export interface EventResponse {
  description?: string; category?: string; posterUrl?: string; durationMinutes?: number | null;
  publication?: string; saleEndTime?: string | null;
  id: string;
  name: string;
  venueName: string;
  venueCity: string;
  saleStartTime: string; // ISO 8601
  eventTime: string;     // ISO 8601
}

export interface SeatResponse {
  id: string;
  seatNumber: string;    // e.g. "A1", "A2", ... "A100"
  section: string;       // e.g. "Section A"
  status: SeatStatus;
}

export interface HoldResponse {
  status: 'HELD';
  seatId: string;
  expiresAt: string;
  serverTime: string;
  event: EventResponse;
  seat: SeatResponse;
}
export interface ActiveHoldsResponse { holds: HoldResponse[]; serverTime: string }
export interface BookingReceipt { booking: BookingResponse; event: EventResponse; seats: SeatResponse[] }
export interface BookingHistory { items: BookingReceipt[]; page: number; size: number; totalElements: number; totalPages: number }

export interface BookingResponse {
  id: string;
  userId: string;
  eventId: string;
  status: BookingStatus;
  createdAt: string;     // ISO 8601
  seatIds: string[];
}

export interface ApiError {
  message: string;
  timestamp: string;
}

// Request bodies
export interface HoldRequest {
  userId: string;
}

export type PaymentScenario = 'SUCCESS' | 'FAILURE' | 'DELAYED_SUCCESS';
export type PaymentMethod = 'UPI' | 'CARD' | 'NET_BANKING';
export interface PaymentAttempt {
  id: string; quoteId: string; status: 'PENDING' | 'SUCCEEDED' | 'FAILED' | 'EXPIRED';
  amountMinor: number; currency: string; method: PaymentMethod; scenario: PaymentScenario;
  readyAt: string; expiresAt: string; resultCode: string | null; booking: BookingResponse | null; demo: boolean;
}
export interface BookingConfirmRequest {
  method?: PaymentMethod;
  scenario?: PaymentScenario;
  quoteId?: string;
  userId: string;
  seatIds: string[];
  idempotencyKey: string;
}

export interface PriceQuote {
  serverTime?: string; validUntil?: number;
  id: string; userId: string; eventId: string; currency: string; expiresAt: string;
  subtotalMinor: number; feeMinor: number; totalMinor: number;
  lines: { seatId: string; seatNumber: string; section: string; amountMinor: number }[];
}
