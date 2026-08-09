// useBookingConfirm - TanStack Mutation wrapper for booking confirmation API

import { useMutation } from '@tanstack/react-query';
import { apiClient } from '../lib/api';
import type { BookingConfirmRequest, BookingResponse, ApiError } from '../types/api';
import { useSessionStore } from '../store/sessionStore';
import { useDeveloperModeStore } from '../store/developerModeStore';

interface UseBookingConfirmOptions {
  /** Called on successful confirmation */
  onSuccess?: (response: BookingResponse, variables: BookingConfirmRequest) => void;
  /** Called on error */
  onError?: (error: Error & { status?: number; apiError?: ApiError }, variables: BookingConfirmRequest) => void;
}

/**
 * Custom mutation hook for confirming a booking via POST /api/bookings/confirm
 *
 * Reads idempotencyKey from sessionStore (generated once at checkout entry).
 *
 * On success:
 * - Calls sessionStore.setBookingConfirmed() with the booking ID
 * - Emits a DevEvent of type 'BOOKING_CONFIRM' to developerModeStore
 *
 * On error:
 * - Does NOT emit a dev event (per requirements)
 * - Returns the error for UI to handle (including 409 for expired holds)
 */
export function useBookingConfirm(options: UseBookingConfirmOptions = {}) {
  const { setBookingConfirmed, idempotencyKey, _userId } = useSessionStore();
  const { emitEvent } = useDeveloperModeStore();

  return useMutation<BookingResponse, Error & { status?: number; apiError?: ApiError }, BookingConfirmRequest>({
    mutationFn: async (request) => {
      // Ensure idempotencyKey is included (should be pre-generated in session)
      const payload: BookingConfirmRequest = {
        ...request,
        idempotencyKey: request.idempotencyKey || idempotencyKey || crypto.randomUUID(),
      };

      const response = await apiClient.confirmBooking(payload);
      return response;
    },
    onSuccess: (response, variables) => {
      // Update session store with confirmed booking
      setBookingConfirmed(response.id);

      // Emit developer mode event for BOOKING_CONFIRM
      // Include all held seat IDs from the booking
      emitEvent({
        type: 'BOOKING_CONFIRM',
        timestamp: new Date().toISOString(),
        seatId: response.seatIds[0], // Primary seat for reference
        seatNumber: undefined,
        redisLock: {
          status: 'RELEASED',
          key: `seat:lock:${response.seatIds[0]}`,
          ttl: 0,
          node: 'redis-01',
        },
        database: {
          status: 'COMMITTED',
          transactionId: response.id, // Use booking ID as transaction reference
        },
        cache: {
          status: 'INVALIDATED',
          key: `seats:event:${response.eventId}`,
        },
        kafka: {
          status: 'PUBLISHED',
          topic: 'booking-confirmed',
        },
        apiCall: {
          method: 'POST',
          path: '/api/bookings/confirm',
          statusCode: 200,
          latencyMs: 0,
        },
      });

      // Call optional user callback
      options.onSuccess?.(response, variables);
    },
    onError: (error, variables) => {
      // Do NOT emit dev event on error (per requirements)
      // The UI should handle specific errors:
      // - 409: Hold expired or belongs to different user
      // - 404: User or seat not found
      // - 400: Validation failure
      // - 5xx: Server error
      options.onError?.(error, variables);
    },
    // Retry configuration - retry on network errors but not on 4xx business errors
    retry: (failureCount, error) => {
      // Don't retry on 4xx errors (client errors that won't succeed on retry)
      if (error && 'status' in error) {
        const status = (error as Error & { status: number }).status;
        if (status >= 400 && status < 500) {
          return false;
        }
      }
      // Retry up to 2 times for network/5xx errors
      return failureCount < 2;
    },
    retryDelay: (attemptIndex) => Math.min(1000 * 2 ** attemptIndex, 10000),
  });
}