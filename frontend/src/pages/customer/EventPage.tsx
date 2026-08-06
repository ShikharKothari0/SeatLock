// EventPage - Seat selection page with grid, summary, and hold timer

import { useParams, useNavigate } from 'react-router-dom';
import { useQuery } from '@tanstack/react-query';
import { useEffect, useState } from 'react';
import { apiClient } from '../../lib/api';
import type { SeatResponse } from '../../types/api';
import { useSessionStore } from '../../store/sessionStore';
import { useSeatHold } from '../../hooks/useSeatHold';
import { SeatGrid } from '../../components/customer/SeatGrid';
import { SelectionSummary } from '../../components/customer/SelectionSummary';
import { HoldTimer } from '../../components/customer/HoldTimer';
import { LoadingSpinner } from '../../components/common/LoadingSpinner';
import { StatusBadge } from '../../components/common/StatusBadge';

export function EventPage() {
  const { eventId } = useParams<{ eventId: string }>();
  const navigate = useNavigate();
  const { userId, selectedSeatIds, heldSeatIds, holdExpiresAt, generateIdempotencyKey, setHeld, clearHold } =
    useSessionStore();
  const [selectedSeats, setSelectedSeats] = useState<SeatResponse[]>([]);
  const [holdError, setHoldError] = useState<string | null>(null);
  const [isHolding, setIsHolding] = useState(false);

  const { data: seats, isLoading, error, refetch } = useQuery<SeatResponse[], Error>({
    queryKey: ['seats', eventId],
    queryFn: () => eventId ? apiClient.getSeats(eventId) : Promise.resolve([]),
    refetchInterval: 5_000,
    staleTime: 5_000,
    enabled: !!eventId,
    retry: 3,
    retryDelay: (attemptIndex) => Math.min(1000 * 2 ** attemptIndex, 30000),
  });

  // Derive selected seats from seat data and selectedSeatIds
  useEffect(() => {
    if (seats && selectedSeatIds.length > 0) {
      const selected = seats.filter((seat) => selectedSeatIds.includes(seat.id));
      setSelectedSeats(selected);
    } else {
      setSelectedSeats([]);
    }
  }, [seats, selectedSeatIds]);

  const handleSeatToggle = (seatId: string) => {
    const { selectSeat, deselectSeat } = useSessionStore.getState();
    const isSelected = selectedSeatIds.includes(seatId);
    if (isSelected) {
      deselectSeat(seatId);
    } else {
      selectSeat(seatId);
    }
  };

  // Seat Hold Mutation
  const holdSeatMutation = useSeatHold({
    onSuccess: (response, variables) => {
      // The hook already calls setHeld and emits dev event
      setIsHolding(false);
      setHoldError(null);
      // Navigate to checkout
      navigate('/checkout');
    },
    onError: (err, variables) => {
      setIsHolding(false);
      const status = (err as Error & { status?: number }).status;
      if (status === 409) {
        setHoldError('This seat is no longer available. Please select another seat.');
      } else if (status === 429) {
        setHoldError('Too many requests. Please wait a moment and try again.');
      } else {
        setHoldError(err.message || 'Failed to hold seat. Please try again.');
      }
    },
  });

  const handleProceedToCheckout = async () => {
    if (selectedSeatIds.length === 0) return;

    // Generate idempotency key for this checkout attempt
    generateIdempotencyKey();

    // Hold all selected seats sequentially
    setIsHolding(true);
    setHoldError(null);

    try {
      for (const seatId of selectedSeatIds) {
        await holdSeatMutation.mutateAsync({ seatId, userId });
      }
      // If all holds succeed, navigation happens in onSuccess
    } catch (err) {
      // Error handled in onError callback
      console.error('Hold failed:', err);
    }
  };

  const handleHoldExpiry = () => {
    // Clear hold state and go back to seat selection
    clearHold();
  };

  if (!eventId) {
    return (
      <div className="flex flex-col items-center justify-center min-h-[60vh] gap-4 text-center">
        <StatusBadge status="FAILED" label="Invalid event" />
        <p className="text-slate-400">No event ID provided</p>
      </div>
    );
  }

  if (isLoading) {
    return (
      <div className="flex flex-col items-center justify-center min-h-[60vh] gap-4">
        <LoadingSpinner size="lg" ariaLabel="Loading seat map" />
        <p className="text-slate-400 text-sm">Loading seat map...</p>
      </div>
    );
  }

  if (error) {
    return (
      <div className="flex flex-col items-center justify-center min-h-[60vh] gap-4 text-center">
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
      <div className="flex flex-col items-center justify-center min-h-[60vh] gap-4 text-center">
        <StatusBadge status="INFO" label="No seats found" />
        <p className="text-slate-400 text-sm">No seats available for this event.</p>
      </div>
    );
  }

  // Sort seats by seat number for consistent rendering
  const sortedSeats = [...seats].sort((a, b) => {
    const aNum = parseInt(a.seatNumber.match(/(\d+)$/)?.[1] || '0', 10);
    const bNum = parseInt(b.seatNumber.match(/(\d+)$/)?.[1] || '0', 10);
    return aNum - bNum;
  });

  return (
    <main className="flex-1 max-w-7xl w-full mx-auto px-4 sm:px-6 lg:px-8 py-8">
      {/* Event Header */}
      <div className="mb-6">
        <h1 className="text-3xl font-bold text-white mb-2">Select Your Seats</h1>
        <p className="text-slate-400">
          Click on available seats to select them. Selected seats will be held for 5 minutes
          once you proceed to checkout.
        </p>
        {holdError && (
          <div className="mt-3 p-3 bg-red-900/30 border border-red-700/50 rounded-lg text-red-400 text-sm">
            {holdError}
          </div>
        )}
      </div>

      {/* Main Content - Grid + Summary */}
      <div className="grid grid-cols-1 lg:grid-cols-3 gap-6">
        {/* Seat Grid */}
        <div className="lg:col-span-2">
          <SeatGrid
            eventId={eventId}
            selectedSeatIds={selectedSeatIds}
            heldSeatIds={heldSeatIds}
            onSeatToggle={handleSeatToggle}
          />
        </div>

          {/* Selection Summary + Hold Timer */}
          <div className="space-y-4">
            <SelectionSummary
              selectedSeats={selectedSeats}
              pricePerSeat={499}
            />

            {/* Hold Timer - only show if holds are active */}
            {heldSeatIds.length > 0 && holdExpiresAt && (
              <HoldTimer expiresAt={holdExpiresAt} onExpiry={handleHoldExpiry} />
            )}

            {/* Proceed to Checkout Button */}
            {selectedSeatIds.length > 0 && heldSeatIds.length === 0 && (
              <button
                onClick={handleProceedToCheckout}
                disabled={isHolding}
                className="w-full px-4 py-3 text-sm font-medium text-white bg-indigo-600 hover:bg-indigo-500 rounded-lg transition-colors disabled:opacity-50 disabled:cursor-not-allowed"
              >
                {isHolding ? (
                  <span className="flex items-center justify-center gap-2">
                    <LoadingSpinner size="sm" />
                    Holding Seats...
                  </span>
                ) : (
                  `Proceed to Checkout (${selectedSeatIds.length})`
                )}
              </button>
            )}

            {/* Already have holds - show checkout button */}
            {heldSeatIds.length > 0 && (
              <button
                onClick={() => navigate('/checkout')}
                className="w-full px-4 py-3 text-sm font-medium text-white bg-green-600 hover:bg-green-500 rounded-lg transition-colors"
              >
                Continue to Checkout ({heldSeatIds.length} held)
              </button>
            )}
          </div>
        </div>
      </main>
  );
}