// SeatCell - Individual seat visualization with status-based styling

import type { SeatResponse } from '../../types/api';
import { ROW_LETTERS } from '../../lib/constants';

interface SeatCellProps {
  seat: SeatResponse;
  isSelected: boolean;
  isHeld: boolean;
  onToggle: () => void;
}

function getSeatRow(seatNumber: string): string {
  const match = seatNumber.match(/(\d+)$/);
  if (!match) return 'A';
  const seatIndex = parseInt(match[1], 10) - 1;
  const rowIndex = Math.floor(seatIndex / 10);
  return ROW_LETTERS[rowIndex] || 'A';
}

function getSeatCol(seatNumber: string): string {
  const match = seatNumber.match(/(\d+)$/);
  if (!match) return '1';
  const seatIndex = parseInt(match[1], 10) - 1;
  const colIndex = (seatIndex % 10) + 1;
  return String(colIndex);
}

export function SeatCell({ seat, isSelected, isHeld, onToggle }: SeatCellProps) {
  const row = getSeatRow(seat.seatNumber);
  const col = getSeatCol(seat.seatNumber);

  const isAvailable = seat.status === 'AVAILABLE';
  const isHeldByApi = seat.status === 'HELD';
  const isConfirmed = seat.status === 'CONFIRMED';

  // If we know this seat is held (from local state), override the API status
  const isActuallyHeld = isHeld || isHeldByApi;
  const isActuallyAvailable = isAvailable && !isHeld;

  // Base styles
  const baseClasses = 'w-8 h-8 rounded-full transition-all duration-150 ease-out flex items-center justify-center cursor-pointer select-none';

  let statusClasses = '';
  let tooltip = '';
  let isInteractive = false;

  if (isConfirmed) {
    // Confirmed (sold): gray, not clickable
    statusClasses = 'bg-slate-500 cursor-not-allowed';
    tooltip = 'Sold';
  } else if (isActuallyHeld) {
    // Held: amber at 60% opacity, not clickable
    statusClasses = 'bg-amber-500/60 cursor-not-allowed';
    tooltip = isHeld ? 'Held by you' : 'Held by another user';
  } else if (isActuallyAvailable) {
    if (isSelected) {
      // Selected: violet ring + fill
      statusClasses = 'bg-violet-500 ring-2 ring-violet-400 ring-offset-2 ring-offset-slate-900 shadow-lg shadow-violet-500/30';
    } else {
      // Available: green filled
      statusClasses = 'bg-green-500 hover:bg-green-400 hover:scale-110';
    }
    tooltip = `Seat ${row}${col} - Available`;
    isInteractive = true;
  }

  return (
    <button
      type="button"
      onClick={isInteractive ? onToggle : undefined}
      disabled={!isInteractive}
      className={`${baseClasses} ${statusClasses}`}
      title={tooltip}
      aria-label={tooltip}
      data-seat-id={seat.id}
      data-seat-status={seat.status}
      data-seat-row={row}
      data-seat-col={col}
    >
      <span className="text-[10px] font-mono font-bold text-white drop-shadow">
        {col}
      </span>
    </button>
  );
}