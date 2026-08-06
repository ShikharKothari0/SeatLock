// API Types - Mirror exactly from FRONTEND-INTEGRATION-GUIDE.md Part 2.1

// All UUID fields are strings in ISO 8601 UUID format.
// All timestamps are ISO 8601 strings in UTC.

export type SeatStatus = 'AVAILABLE' | 'HELD' | 'CONFIRMED';

export type BookingStatus = 'HELD' | 'CONFIRMED' | 'EXPIRED' | 'CANCELLED';

export interface EventResponse {
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
}

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

export interface BookingConfirmRequest {
  userId: string;
  seatIds: string[];
  idempotencyKey: string;
}