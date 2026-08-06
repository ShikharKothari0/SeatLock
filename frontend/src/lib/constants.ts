// Constants - Hardcoded values for development/demo

/**
 * Seed data UUIDs from FRONTEND-INTEGRATION-GUIDE.md Part 3
 * All seed data is deterministic — the same UUIDs every time the database is reset.
 */

// Event: Test Concert
export const EVENT_ID = '22222222-2222-2222-2222-222222222222';

// User: testuser@seatlock.com
export const USER_ID = '33333333-3333-3333-3333-333333333333';

// Hold duration: 5 minutes = 300,000 ms
export const HOLD_DURATION_MS = 300_000;

// Seat sections (derived from seat index: 0-9=A, 10-19=B, etc.)
export const SEAT_SECTIONS = [
  'Section A', 'Section B', 'Section C', 'Section D', 'Section E',
  'Section F', 'Section G', 'Section H', 'Section I', 'Section J',
] as const;

export type SeatSection = typeof SEAT_SECTIONS[number];

// Row letters for 10x10 grid display
export const ROW_LETTERS = ['A', 'B', 'C', 'D', 'E', 'F', 'G', 'H', 'I', 'J'] as const;

export type RowLetter = typeof ROW_LETTERS[number];

// Price constants (hardcoded for demo)
export const PRICE_PER_SEAT = 499; // ₹499
export const PLATFORM_FEE = 149;   // ₹149
export const TAXES = 99;           // ₹99

// Rate limiting constants
export const HOLD_RATE_LIMIT_MAX = 5;
export const HOLD_RATE_LIMIT_WINDOW_MS = 10_000;

// Polling intervals
export const SEAT_POLL_INTERVAL_MS = 5_000;
export const METRICS_POLL_INTERVAL_MS = 5_000;
export const HEALTH_POLL_INTERVAL_MS = 10_000;
export const SSE_RECONNECT_DELAY_MS = 3_000;

// Status badge color mapping
// Maps status strings to Tailwind CSS classes for StatusBadge component
export const STATUS_BADGE_CLASSES: Record<string, string> = {
  // Success states - green
  ACQUIRED: 'status-success',
  CONFIRMED: 'status-success',
  CONNECTED: 'status-success',
  CLOSED: 'status-success',
  SUCCESS: 'status-success',

  // Pending/warning states - amber
  PENDING: 'status-pending',
  WAITING: 'status-pending',
  HELD: 'status-pending',
  HALF_OPEN: 'status-pending',

  // Failure/error states - red
  FAILED: 'status-failure',
  OPEN: 'status-failure',
  DISCONNECTED: 'status-failure',
  REJECTED: 'status-failure',
  EXPIRED: 'status-failure',
  CANCELLED: 'status-failure',

  // Info states - blue
  MISS: 'status-info',
  PUBLISHED: 'status-info',
  INFO: 'status-info',
  AVAILABLE: 'status-info',
} as const;

// Human-readable status labels
export const STATUS_LABELS: Record<string, string> = {
  ACQUIRED: 'Acquired',
  CONFIRMED: 'Confirmed',
  CONNECTED: 'Connected',
  CLOSED: 'Closed',
  SUCCESS: 'Success',
  PENDING: 'Pending',
  WAITING: 'Waiting',
  HELD: 'Held',
  HALF_OPEN: 'Half Open',
  FAILED: 'Failed',
  OPEN: 'Open',
  DISCONNECTED: 'Disconnected',
  REJECTED: 'Rejected',
  EXPIRED: 'Expired',
  CANCELLED: 'Cancelled',
  MISS: 'Miss',
  PUBLISHED: 'Published',
  INFO: 'Info',
  AVAILABLE: 'Available',
} as const;

// Helper to get badge class for any status
export function getStatusBadgeClass(status: string): string {
  return STATUS_BADGE_CLASSES[status] ?? 'status-info';
}

// Helper to get human-readable label for any status
export function getStatusLabel(status: string): string {
  return STATUS_LABELS[status] ?? status;
}

// API endpoints (for reference, actual calls use apiClient)
export const ENDPOINTS = {
  EVENTS: '/api/events',
  EVENT_BY_ID: (id: string) => `/api/events/${id}`,
  SEATS: (eventId: string) => `/api/events/${eventId}/seats`,
  SEAT_HOLD: (seatId: string) => `/api/seats/${seatId}/hold`,
  BOOKING_CONFIRM: '/api/bookings/confirm',
  ADMIN_OVERVIEW: '/api/admin/metrics/overview',
  ADMIN_CACHE: '/api/admin/metrics/cache',
  ADMIN_REDIS: '/api/admin/metrics/redis',
  ADMIN_CIRCUIT_BREAKERS: '/api/admin/metrics/circuit-breakers',
  ADMIN_HEALTH: '/api/admin/health',
  ADMIN_METRICS_STREAM: '/api/admin/metrics/stream',
} as const;

// SSE event names
export const SSE_EVENTS = {
  METRICS: 'metrics',
} as const;

// LocalStorage keys
export const STORAGE_KEYS = {
  THEME: 'seatlock:theme',
  DEVELOPER_MODE: 'seatlock:devMode',
} as const;