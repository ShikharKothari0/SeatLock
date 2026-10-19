import { useMutation, useQueryClient } from '@tanstack/react-query';
import { apiClient } from '../lib/api';
import type { BookingResponse, PaymentAttempt, PaymentMethod, PaymentScenario } from '../types/api';
import { useSessionStore } from '../store/sessionStore';
import { useDeveloperModeStore } from '../store/developerModeStore';

type BookingError = Error & { status?: number };
export function useBookingConfirm(options: {
  onSuccess?: (response: BookingResponse) => void;
  onFailed?: (attempt: PaymentAttempt) => void;
  onError?: (error: BookingError) => void;
} = {}) {
  const queryClient = useQueryClient();
  return useMutation<PaymentAttempt, BookingError, { method: PaymentMethod; scenario: PaymentScenario }>({
    mutationFn: async ({ method, scenario }) => {
      // The persisted request wins over current controls on every retry and refresh.
      const request = useSessionStore.getState().beginConfirmation(method, scenario);
      const attempt = await apiClient.startPayment(request);
      return attempt.status === 'PENDING' ? apiClient.processPayment(attempt.id) : attempt;
    },
    onSuccess: attempt => {
      if (attempt.status === 'PENDING') return;
      if (attempt.status !== 'SUCCEEDED' || !attempt.booking) {
        useSessionStore.getState().rejectConfirmation();
        options.onFailed?.(attempt);
        return;
      }
      const response = attempt.booking;
      useDeveloperModeStore.getState().emitEvent({
        type: 'BOOKING_CONFIRM', timestamp: new Date().toISOString(), seatId: response.seatIds[0],
        kafka: { status: 'UNVERIFIED', topic: 'seat-confirmed' },
      });
      useSessionStore.getState().setBookingConfirmed(response);
      void queryClient.invalidateQueries({ queryKey: ['seats', response.eventId] });
      void queryClient.invalidateQueries({ queryKey: ['admin'] });
      void queryClient.invalidateQueries({ queryKey: ['bookings'] });
      options.onSuccess?.(response);
    },
    onError: error => {
      if (error.status && error.status >= 400 && error.status < 500 && error.status !== 401 && error.status !== 403) {
        useSessionStore.getState().rejectConfirmation();
      }
      options.onError?.(error);
    },
    retry: (count, error) => count < 2 && (error.status === 0 || (error.status ?? 0) >= 500),
    retryDelay: attempt => Math.min(1000 * 2 ** attempt, 5000),
  });
}
