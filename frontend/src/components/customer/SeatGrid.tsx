// Section-based seat map with availability polling and event-specific prices.

import { useQuery } from '@tanstack/react-query';
import { apiClient } from '../../lib/api';
import type { SeatResponse } from '../../types/api';
import { SEAT_POLL_INTERVAL_MS } from '../../lib/constants';
import { SeatCell } from './SeatCell';
import { LoadingSpinner } from '../common/LoadingSpinner';
import api from '../../lib/api';
import { SectionSeatMap } from './SectionSeatMap';
import type { SectionLayout } from '../../lib/seatLayout';
import { StatusBadge } from '../common/StatusBadge';

interface SeatGridProps {
  eventId: string;
  selectedSeatIds: string[];
  heldSeatIds: string[];
  onSeatToggle: (seatId: string) => void;
  disabled?: boolean;
}

export function SeatGrid({ eventId, selectedSeatIds, heldSeatIds, onSeatToggle, disabled = false }: SeatGridProps) {
  const { data: seats, isLoading, error, refetch } = useQuery<SeatResponse[], Error>({
    queryKey: ['seats', eventId],
    queryFn: () => apiClient.getSeats(eventId),
    refetchInterval: SEAT_POLL_INTERVAL_MS,
    staleTime: SEAT_POLL_INTERVAL_MS,
    retry: 3,
    retryDelay: (attemptIndex) => Math.min(1000 * 2 ** attemptIndex, 30000),
  });

  const prices = useQuery({ queryKey: ['prices', eventId], queryFn: () => api.get<{sections: SectionLayout[]}>(`/events/${eventId}/prices`).then(r=>r.data) });

  if (isLoading || prices.isLoading) {
    return (
      <div className="flex flex-col items-center justify-center py-12 gap-4">
        <LoadingSpinner size="lg" ariaLabel="Loading seat map" />
        <p className="text-slate-600 dark:text-slate-400 text-sm">Loading seat map...</p>
      </div>
    );
  }

  if (error || prices.error) {
    return (
      <div className="flex flex-col items-center justify-center py-12 gap-4 text-center">
        <StatusBadge status="FAILED" label="Failed to load seats" />
        <p className="text-slate-600 dark:text-slate-400 text-sm max-w-xs">
          We couldn’t load the seat map. Please try again.
        </p>
        <button
          onClick={() => { void refetch(); void prices.refetch(); }}
          className="mt-2 px-4 py-2 text-sm font-medium text-white bg-indigo-600 hover:bg-indigo-500 rounded-lg transition-colors"
        >
          Retry
        </button>
      </div>
    );
  }

  if (!seats || seats.length === 0) {
    return (
      <div className="flex flex-col items-center justify-center py-12 gap-4 text-center">
        <StatusBadge status="INFO" label="No seats found" />
        <p className="text-slate-600 dark:text-slate-400 text-sm">No seats available for this event.</p>
      </div>
    );
  }

  return (
    <div className="space-y-4">
      <SectionSeatMap seats={seats} layouts={prices.data?.sections ?? []} renderSeat={(seat,row,column)=>(
        <SeatCell seat={seat} row={row} column={column} disabled={disabled} isSelected={selectedSeatIds.includes(seat.id)} isHeld={heldSeatIds.includes(seat.id)} onToggle={()=>onSeatToggle(seat.id)} />
      )} />
      {/* Legend */}
      <div className="flex flex-wrap justify-center gap-4 text-sm text-slate-600 dark:text-slate-400">
        <div className="flex items-center gap-1.5">
          <StatusBadge status="INFO" label="Legend" className="text-xs" />
        </div>
        <div className="flex items-center gap-1">
          <span className="w-3 h-3 rounded-full bg-green-500" />
          <span>Available</span>
        </div>
        <div className="flex items-center gap-1">
          <span className="w-3 h-3 rounded-full bg-amber-500/60" />
          <span>Reserved</span>
        </div>
        <div className="flex items-center gap-1">
          <span className="w-3 h-3 rounded-full bg-violet-500 ring-1 ring-violet-400" />
          <span>Selected</span>
        </div>
        <div className="flex items-center gap-1">
          <span className="w-3 h-3 rounded-full bg-slate-500" />
          <span>Sold</span>
        </div>
      </div>
    </div>
  );
}
