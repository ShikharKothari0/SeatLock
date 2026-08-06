// useSeatHold - TanStack Mutation wrapper for seat hold API

import { useMutation } from '@tanstack/react-query';
import { apiClient } from '../lib/api';
import type { HoldRequest, HoldResponse } from '../types/api';
import { HOLD_DURATION_MS } from '../lib/constants';
import { useSessionStore } from '../store/sessionStore';
import { useDeveloperModeStore } from '../store/developerModeStore';

interface UseSeatHoldOptions {
  /** Called on successful hold - can be used for side effects */
  onSuccess?: (response: HoldResponse, variables: { seatId: string; userId: string }) => void;
  /** Called on error - can be used for error handling */
  onError?: (error: Error, variables: { seatId: string; userId: string }) => void;
}

/**
 * Custom mutation hook for holding a seat via POST /api/seats/{seatId}/hold
 *
 * On success:
 * - Calls sessionStore.setHeld() with the seat ID and calculated expiry
 * - Emits a DevEvent of type 'SEAT_HOLD' to developerModeStore
 *
 * On error:
 * - Does NOT emit a dev event (per requirements)
 * - Returns the error for UI to handle
 */
export function useSeatHold(options: UseSeatHoldOptions = {}) {
  const { setHeld } = useSessionStore();
  const { emitEvent } = useDeveloperModeStore();

  return useMutation<HoldResponse, Error, { seatId: string; userId: string }>({
    mutationFn: async ({ seatId, userId }) => {
      const response = await apiClient.holdSeat(seatId, userId);
      return response;
    },
    onSuccess: (response, variables) => {
      // Calculate expiry timestamp: now + 5 minutes
      const expiresAt = Date.now() + HOLD_DURATION_MS;

      // Update session store with held seat and expiry
      setHeld([response.seatId], expiresAt);

      // Emit developer mode event for SEAT_HOLD
      emitEvent({
        type: 'SEAT_HOLD',
        timestamp: new Date().toISOString(),
        seatId: response.seatId,
        seatNumber: undefined, // Not available in hold response
        redisLock: {
          status: 'ACQUIRED',
          key: `seat:lock:${response.seatId}`,
          ttl: HOLD_DURATION_MS,
          node: 'redis-01',
        },
        database: {
          status: 'COMMITTED',
          transactionId: null, // Not available in response
        },
        cache: {
          status: 'INVALIDATED',
          key: `seats:event:${variables.seatId}`, // Approximate cache key
        },
        kafka: {
          status: 'WAITING', // Initially waiting, will be PUBLISHED later
          topic: 'seat-held',
        },
        apiCall: {
          method: 'POST',
          path: `/api/seats/${variables.seatId}/hold`,
          statusCode: 200,
          latencyMs: 0, // Not available from axios directly
        },
      });

      // Call optional user callback
      options.onSuccess?.(response, variables);
    },
    onError: (error, variables) => {
      // Do NOT emit dev event on error (per requirements)
      // Just pass error to UI for handling
      options.onError?.(error, variables);
    },
    // Retry configuration for transient failures
    retry: (failureCount, error) => {
      // Don't retry on 409 (conflict) or 429 (rate limit) - these are expected business errors
      if (error && 'status' in error) {
        const status = (error as Error & { status: number }).status;
        if (status === 409 || status === 429) {
          return false;
        }
      }
      // Retry up to 2 times for other errors (network, 5xx)
      return failureCount < 2;
    },
    retryDelay: (attemptIndex) => Math.min(1000 * 2 ** attemptIndex, 10000),
  });
}