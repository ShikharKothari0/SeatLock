import { useMutation, useQueryClient } from '@tanstack/react-query';
import { apiClient } from '../lib/api';
import type { BookingResponse } from '../types/api';
import { useSessionStore } from '../store/sessionStore';
import { useDeveloperModeStore } from '../store/developerModeStore';

type BookingError = Error & { status?: number };
export function useBookingConfirm(options: {
  onSuccess?: (response: BookingResponse) => void;
  onError?: (error: BookingError) => void;
} = {}) {
  const queryClient = useQueryClient();
  return useMutation<BookingResponse, BookingError, void>({
    mutationFn: async () => {
      const request = useSessionStore.getState().beginConfirmation();
      const start = Date.now();
      const response = await apiClient.confirmBooking(request);
      useDeveloperModeStore.getState().emitEvent({
        type: 'BOOKING_CONFIRM', timestamp: new Date().toISOString(),
        seatId: response.seatIds[0],
        apiCall: { method: 'POST', path: '/api/bookings/confirm', statusCode: 200, latencyMs: Date.now() - start },
        kafka: { status: 'UNVERIFIED', topic: 'seat-confirmed' },
      });
      return response;
    },
    onSuccess: response => {
      useSessionStore.getState().setBookingConfirmed(response);
      void queryClient.invalidateQueries({ queryKey: ['seats', response.eventId] });
      void queryClient.invalidateQueries({ queryKey: ['admin'] });
      void queryClient.invalidateQueries({ queryKey: ['bookings'] });
      options.onSuccess?.(response);
    },
    onError: error => {
      if (error.status && error.status >= 400 && error.status < 500) {
        useSessionStore.getState().rejectConfirmation();
      }
      options.onError?.(error);
    },
    retry: (count, error) => count < 2 && (error.status === 0 || (error.status ?? 0) >= 500),
    retryDelay: attempt => Math.min(1000 * 2 ** attempt, 5000),
  });
}
