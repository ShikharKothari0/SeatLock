// SeatGrid - 10x10 seat grid with polling, row labels, and legend

import { useQuery } from '@tanstack/react-query';
import { apiClient } from '../../lib/api';
import type { SeatResponse, SeatStatus } from '../../types/api';
import { SEAT_POLL_INTERVAL_MS, ROW_LETTERS } from '../../lib/constants';
import { SeatCell } from './SeatCell';
import { LoadingSpinner } from '../common/LoadingSpinner';
import { StatusBadge } from '../common/StatusBadge';

interface SeatGridProps {
  eventId: string;
  selectedSeatIds: string[];
  heldSeatIds: string[];
  onSeatToggle: (seatId: string) => void;
}

const LEGEND_ITEMS = [
  { status: 'AVAILABLE' as SeatStatus, label: 'Available', color: 'status-success' },
  { status: 'HELD' as SeatStatus, label: 'Held', color: 'status-pending' },
  { status: 'CONFIRMED' as SeatStatus, label: 'Sold', color: 'status-failure' },
  { status: 'SELECTED', label: 'Selected', color: 'bg-violet-500' },
] as const;

export function SeatGrid({ eventId, selectedSeatIds, heldSeatIds, onSeatToggle }: SeatGridProps) {
  const { data: seats, isLoading, error, refetch } = useQuery<SeatResponse[], Error>({
    queryKey: ['seats', eventId],
    queryFn: () => apiClient.getSeats(eventId),
    refetchInterval: SEAT_POLL_INTERVAL_MS,
    staleTime: SEAT_POLL_INTERVAL_MS,
    retry: 3,
    retryDelay: (attemptIndex) => Math.min(1000 * 2 ** attemptIndex, 30000),
  });

  if (isLoading) {
    return (
      <div className="flex flex-col items-center justify-center py-12 gap-4">
        <LoadingSpinner size="lg" ariaLabel="Loading seat map" />
        <p className="text-slate-400 text-sm">Loading seat map...</p>
      </div>
    );
  }

  if (error) {
    return (
      <div className="flex flex-col items-center justify-center py-12 gap-4 text-center">
        <StatusBadge status="FAILED" label="Failed to load seats" />
        <p className="text-slate-400 text-sm max-w-xs">
          {error.message}. Click to retry.
        </p>
        <button
          onClick={() => refetch()}
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
        <p className="text-slate-400 text-sm">No seats available for this event.</p>
      </div>
    );
  }

  // Sort seats by row then column (A1, A2, ..., A10, B1, B2, ...)
  const sortedSeats = [...seats].sort((a, b) => {
    const aNum = parseInt(a.seatNumber.match(/(\d+)$/)?.[1] || '0', 10);
    const bNum = parseInt(b.seatNumber.match(/(\d+)$/)?.[1] || '0', 10);
    return aNum - bNum;
  });

  // Group seats by row (10 seats per row)
  const rows: Record<string, SeatResponse[]> = {};
  for (const seat of sortedSeats) {
    const match = seat.seatNumber.match(/(\d+)$/);
    if (!match) continue;
    const seatIndex = parseInt(match[1], 10) - 1;
    const rowIndex = Math.floor(seatIndex / 10);
    const rowLetter = ROW_LETTERS[rowIndex] || 'A';
    if (!rows[rowLetter]) rows[rowLetter] = [];
    rows[rowLetter].push(seat);
  }

  return (
    <div className="space-y-4">
      {/* Stage indicator */}
      <div className="flex items-center justify-center">
        <div className="w-full max-w-md px-4">
          <div className="relative">
            <div className="absolute inset-0 flex items-center">
              <div className="w-full border-t border-dashed border-slate-700" />
            </div>
            <div className="relative flex justify-center">
              <span className="bg-slate-950 px-3 text-xs font-mono text-slate-500 uppercase tracking-wider">
                STAGE
              </span>
            </div>
          </div>
        </div>
      </div>

      {/* Seat Grid */}
      <div className="flex justify-center">
        <div className="space-y-2 max-w-md">
          {ROW_LETTERS.map((rowLetter) => {
            const rowSeats = rows[rowLetter] || [];
            return (
              <div key={rowLetter} className="flex items-center gap-2">
                <span className="w-6 text-right text-xs font-mono text-slate-500 select-none">
                  {rowLetter}
                </span>
                <div className="flex gap-2">
                  {rowSeats.map((seat) => (
                    <SeatCell
                      key={seat.id}
                      seat={seat}
                      isSelected={selectedSeatIds.includes(seat.id)}
                      isHeld={heldSeatIds.includes(seat.id)}
                      onToggle={() => onSeatToggle(seat.id)}
                    />
                  ))}
                  {/* Fill empty slots if row has fewer than 10 seats */}
                  {rowSeats.length < 10 && (
                    <>
                      {Array.from({ length: 10 - rowSeats.length }).map((_, i) => (
                        <div key={`empty-${i}`} className="w-8 h-8" />
                      ))}
                    </>
                  )}
                </div>
              </div>
            );
          })}
        </div>
      </div>

      {/* Legend */}
      <div className="flex flex-wrap justify-center gap-4 text-sm text-slate-400">
        <div className="flex items-center gap-1.5">
          <StatusBadge status="INFO" label="Legend" className="text-xs" />
        </div>
        <div className="flex items-center gap-1">
          <span className="w-3 h-3 rounded-full bg-green-500" />
          <span>Available</span>
        </div>
        <div className="flex items-center gap-1">
          <span className="w-3 h-3 rounded-full bg-amber-500/60" />
          <span>Held</span>
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