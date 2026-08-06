// Developer Mode Store - Zustand store for developer console events

import { create } from 'zustand';

/**
 * DevEvent interface - mirrors the event structure emitted during seat hold/booking flow
 * Referenced in FRONTEND-INTEGRATION-GUIDE.md Block 3 and Block 7
 */
export interface DevEvent {
  /** Event type identifier */
  type: 'SEAT_HOLD' | 'BOOKING_CONFIRM' | 'SEAT_RELEASE' | 'HOLD_EXPIRED' | 'ERROR';
  /** ISO 8601 timestamp when event occurred */
  timestamp: string;
  /** Seat ID involved (if applicable) */
  seatId?: string;
  /** Seat number (e.g., "A12") */
  seatNumber?: string;
  /** Redis lock information */
  redisLock?: {
    /** Status: 'ACQUIRED' | 'RELEASED' | 'EXPIRED' | 'FAILED' */
    status: string;
    /** Redis key (e.g., "seat:lock:xxx") */
    key: string;
    /** TTL in milliseconds */
    ttl: number;
    /** Redis node identifier */
    node: string;
  };
  /** Database transaction information */
  database?: {
    /** Status: 'COMMITTED' | 'ROLLED_BACK' | 'PENDING' */
    status: string;
    /** Transaction ID or null */
    transactionId: string | null;
  };
  /** Cache information */
  cache?: {
    /** Status: 'HIT' | 'MISS' | 'UPDATED' | 'INVALIDATED' */
    status: string;
    /** Cache key */
    key: string;
  };
  /** Kafka event information */
  kafka?: {
    /** Status: 'PUBLISHED' | 'WAITING' | 'FAILED' */
    status: string;
    /** Kafka topic */
    topic: string;
    /** Partition (optional) */
    partition?: number;
    /** Offset (optional) */
    offset?: number;
  };
  /** API call information */
  apiCall?: {
    /** HTTP method */
    method: string;
    /** API path */
    path: string;
    /** HTTP status code */
    statusCode: number;
    /** Latency in milliseconds */
    latencyMs: number;
  };
  /** Error information (for ERROR type events) */
  error?: {
    /** Error message */
    message: string;
    /** HTTP status code if applicable */
    statusCode?: number;
  };
}

interface DeveloperModeState {
  enabled: boolean;
  events: DevEvent[]; // Last 50, most recent first

  toggle: () => void;
  setEnabled: (enabled: boolean) => void;
  emitEvent: (event: DevEvent) => void;
  clearEvents: () => void;
}

const MAX_EVENTS = 50;

export const useDeveloperModeStore = create<DeveloperModeState>((set) => ({
  enabled: false,
  events: [],

  toggle: () => set((state) => ({ enabled: !state.enabled })),
  setEnabled: (enabled: boolean) => set({ enabled }),

  emitEvent: (event: DevEvent) =>
    set((state) => ({
      events: [event, ...state.events].slice(0, MAX_EVENTS),
    })),

  clearEvents: () => set({ events: [] }),
}));