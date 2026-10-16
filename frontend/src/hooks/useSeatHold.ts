import { useMutation, useQueryClient } from '@tanstack/react-query';
import { apiClient } from '../lib/api';
import { localDeadline } from '../lib/reservations';
import { useSessionStore } from '../store/sessionStore';
import { useDeveloperModeStore } from '../store/developerModeStore';

export function useSeatHold() {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: async ({ seatId, userId, eventId }: { seatId: string; userId: string; eventId: string }) => {
      const startedAt = Date.now();
      const response = await apiClient.holdSeat(seatId, userId);
      return { response, expiresAt: localDeadline(response, startedAt), latencyMs: Date.now() - startedAt, eventId };
    },
    onSuccess: ({ response, expiresAt, latencyMs, eventId }) => {
      useSessionStore.getState().setHeld([response.seatId], expiresAt);
      useDeveloperModeStore.getState().emitEvent({
        type: 'SEAT_HOLD', timestamp: new Date().toISOString(), seatId: response.seatId,
        apiCall: { method: 'POST', path: `/api/seats/${response.seatId}/hold`, statusCode: 200, latencyMs },
        kafka: { status: 'UNVERIFIED', topic: 'seat-held' },
      });
      void queryClient.invalidateQueries({ queryKey: ['seats', eventId] });
    },
    // Hold creation is not idempotent. A timeout may already have created the hold.
    retry: false,
  });
}
