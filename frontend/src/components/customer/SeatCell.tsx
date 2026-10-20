// SeatCell - Individual seat visualization with status-based styling

import type { SeatResponse } from '../../types/api';


interface SeatCellProps {
  seat: SeatResponse;
  isSelected: boolean;
  isHeld: boolean;
  onToggle: () => void;
  disabled?: boolean;
  row?: string;
  column?: number;
}

export function SeatCell({ seat, row = 'A', column = 1, isSelected, isHeld, onToggle, disabled = false }: SeatCellProps) {
  const col = String(column);

  const isAvailable = seat.status === 'AVAILABLE';
  const isHeldByApi = seat.status === 'HELD';
  const isConfirmed = seat.status === 'CONFIRMED';

  // If we know this seat is held (from local state), override the API status
  const isActuallyHeld = isHeld || isHeldByApi;
  const isActuallyAvailable = isAvailable && !isHeld;

  // Base styles
  const baseClasses = 'min-w-10 h-10 px-1 rounded-md transition-all duration-150 ease-out flex items-center justify-center cursor-pointer select-none';

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
    tooltip = isHeld ? 'Reserved for you' : 'Temporarily unavailable';
  } else if (isActuallyAvailable) {
    if (isSelected) {
      // Selected: violet ring + fill
      statusClasses = 'bg-violet-500 ring-2 ring-violet-400 ring-offset-2 ring-offset-slate-900 shadow-lg shadow-violet-500/30';
    } else {
      // Available: green filled
      statusClasses = 'bg-green-500 hover:bg-green-400 hover:scale-110';
    }
    tooltip = `Seat ${seat.seatNumber} - ${isSelected ? 'Selected' : 'Available'}`;
    isInteractive = true;
  }

  return (
    <button
      type="button"
      onClick={isInteractive ? onToggle : undefined}
      disabled={disabled || !isInteractive}
      aria-pressed={isSelected}
      className={`${baseClasses} ${statusClasses}`}
      title={`${seat.section} · ${seat.seatNumber} · ${tooltip}`}
      aria-label={`Seat ${seat.seatNumber}, ${seat.section}, row ${row}: ${tooltip}`}
      data-seat-id={seat.id}
      data-seat-status={seat.status}
      data-seat-row={row}
      data-seat-col={col}
    >
      <span className="text-[10px] font-mono font-bold text-slate-900 dark:text-white drop-shadow">
        {seat.seatNumber}
      </span>
    </button>
  );
}
