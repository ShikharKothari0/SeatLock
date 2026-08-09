// CheckoutPage - Review selected seats and proceed to payment

import { useNavigate } from 'react-router-dom';
import { useSessionStore } from '../../store/sessionStore';
import { HoldTimer } from '../../components/customer/HoldTimer';
import { LoadingSpinner } from '../../components/common/LoadingSpinner';
import { StatusBadge } from '../../components/common/StatusBadge';
import { PRICE_PER_SEAT, PLATFORM_FEE, TAXES } from '../../lib/constants';

export function CheckoutPage() {
  const navigate = useNavigate();
  const { selectedSeatIds, heldSeatIds, holdExpiresAt, idempotencyKey, generateIdempotencyKey } =
    useSessionStore();

  // If no seats selected, go back to event page
  useEffect(() => {
    if (selectedSeatIds.length === 0 && heldSeatIds.length === 0) {
      navigate('/');
    }
  }, [selectedSeatIds, heldSeatIds, navigate]);

  // We don't need to query seats here - we use the seat IDs from session store
  const isLoading = false;
  const error: Error | null = null;

  const handleBackToSeats = () => {
    navigate(-1);
  };

  const handleProceedToPayment = () => {
    // Generate idempotency key if not already generated
    if (!idempotencyKey) {
      generateIdempotencyKey();
    }
    navigate('/payment');
  };

  const handleHoldExpiry = () => {
    useSessionStore.getState().clearHold();
    navigate('/');
  };

  if (isLoading) {
    return (
      <div className="flex flex-col items-center justify-center min-h-[60vh] gap-4">
        <LoadingSpinner size="lg" ariaLabel="Loading checkout" />
        <p className="text-slate-400 text-sm">Loading checkout...</p>
      </div>
    );
  }

  if (error) {
    return (
      <div className="flex flex-col items-center justify-center min-h-[60vh] gap-4 text-center">
        <StatusBadge status="FAILED" label="Failed to load checkout" />
        <p className="text-slate-400 text-sm max-w-xs">
          {error.message}
        </p>
        <button
          onClick={() => window.location.reload()}
          className="mt-2 px-4 py-2 text-sm font-medium text-white bg-indigo-600 hover:bg-indigo-500 rounded-lg transition-colors"
        >
          Reload Page
        </button>
      </div>
    );
  }

  // Calculate totals
  const seatCount = Math.max(selectedSeatIds.length, heldSeatIds.length);
  const subtotal = seatCount * PRICE_PER_SEAT;
  const total = subtotal + PLATFORM_FEE + TAXES;

  return (
    <main className="flex-1 max-w-7xl w-full mx-auto px-4 sm:px-6 lg:px-8 py-8">
      {/* Header */}
      <div className="mb-6 flex items-center justify-between">
          <div>
            <h1 className="text-3xl font-bold text-white mb-2">Checkout</h1>
            <p className="text-slate-400">Review your selection and proceed to payment</p>
          </div>
          <button
            onClick={handleBackToSeats}
            className="px-4 py-2 text-sm font-medium text-slate-300 hover:text-white bg-slate-800/50 border border-slate-600/50 rounded-lg transition-colors"
          >
            ← Back to Seats
          </button>
        </div>

        <div className="grid grid-cols-1 lg:grid-cols-3 gap-6">
          {/* Order Summary - spans 2 columns on lg */}
          <div className="lg:col-span-2 space-y-6">
            {/* Seats List */}
            <div className="bg-slate-800/50 border border-slate-600/50 rounded-xl p-5">
              <h3 className="text-lg font-semibold text-white mb-4">Your Seats</h3>
              <div className="space-y-3">
                {selectedSeatIds.map((seatId, index) => (
                  <div
                    key={seatId}
                    className="flex items-center justify-between py-2 px-3 bg-slate-900/50 rounded-lg border border-slate-700/50"
                  >
                    <div className="flex items-center gap-3">
                      <span className="w-8 h-8 rounded-full bg-violet-500 ring-2 ring-violet-400 ring-offset-2 ring-offset-slate-900 flex items-center justify-center">
                        <span className="text-[10px] font-mono font-bold text-white">{index + 1}</span>
                      </span>
                      <span className="font-mono text-sm text-white">Seat {seatId.slice(-8)}</span>
                    </div>
                    <span className="font-mono text-sm text-green-400">₹{PRICE_PER_SEAT.toLocaleString()}</span>
                  </div>
                ))}
                {heldSeatIds.map((seatId, index) => (
                  <div
                    key={`held-${seatId}`}
                    className="flex items-center justify-between py-2 px-3 bg-slate-900/50 rounded-lg border border-slate-700/50"
                  >
                    <div className="flex items-center gap-3">
                      <span className="w-8 h-8 rounded-full bg-amber-500 ring-2 ring-amber-400 ring-offset-2 ring-offset-slate-900 flex items-center justify-center">
                        <span className="text-[10px] font-mono font-bold text-white">{selectedSeatIds.length + index + 1}</span>
                      </span>
                      <span className="font-mono text-sm text-white">Seat {seatId.slice(-8)} (Held)</span>
                    </div>
                    <span className="font-mono text-sm text-green-400">₹{PRICE_PER_SEAT.toLocaleString()}</span>
                  </div>
                ))}
                {(selectedSeatIds.length === 0 && heldSeatIds.length === 0) && (
                  <div className="text-center py-8 text-slate-400">
                    No seats in your order
                  </div>
                )}
              </div>
            </div>

            {/* Price Breakdown */}
            <div className="bg-slate-800/50 border border-slate-600/50 rounded-xl p-5">
              <h3 className="text-lg font-semibold text-white mb-4">Price Details</h3>
              <div className="space-y-3">
                <div className="flex justify-between text-sm">
                  <span className="text-slate-400">{seatCount} seat{seatCount > 1 ? 's' : ''} × ₹{PRICE_PER_SEAT.toLocaleString()}</span>
                  <span className="font-mono text-white">₹{subtotal.toLocaleString()}</span>
                </div>
                <div className="flex justify-between text-sm">
                  <span className="text-slate-400">Platform fee</span>
                  <span className="font-mono text-white">₹{PLATFORM_FEE.toLocaleString()}</span>
                </div>
                <div className="flex justify-between text-sm">
                  <span className="text-slate-400">Taxes</span>
                  <span className="font-mono text-white">₹{TAXES.toLocaleString()}</span>
                </div>
                <div className="flex justify-between text-lg font-semibold border-t border-slate-700/50 pt-3">
                  <span className="text-white">Total</span>
                  <span className="font-mono text-green-400">₹{total.toLocaleString()}</span>
                </div>
              </div>
            </div>

            {/* Hold Timer */}
            {heldSeatIds.length > 0 && holdExpiresAt && (
              <HoldTimer expiresAt={holdExpiresAt} onExpiry={handleHoldExpiry} />
            )}

            {/* Proceed to Payment Button */}
            <button
              onClick={handleProceedToPayment}
              disabled={seatCount === 0}
              className="w-full px-4 py-3 text-sm font-medium text-white bg-indigo-600 hover:bg-indigo-500 rounded-lg transition-colors disabled:opacity-50 disabled:cursor-not-allowed"
            >
              Proceed to Payment
            </button>
          </div>

          {/* Sidebar - Empty for now, could show event info */}
          <div className="lg:col-span-1">
            <div className="bg-slate-800/50 border border-slate-600/50 rounded-xl p-5 sticky top-24">
              <h3 className="text-lg font-semibold text-white mb-4">Order Summary</h3>
              <div className="space-y-2 text-sm">
                <div className="flex justify-between">
                  <span className="text-slate-400">Subtotal</span>
                  <span className="font-mono text-white">₹{subtotal.toLocaleString()}</span>
                </div>
                <div className="flex justify-between">
                  <span className="text-slate-400">Fees & Taxes</span>
                  <span className="font-mono text-white">₹{PLATFORM_FEE + TAXES}</span>
                </div>
                <div className="flex justify-between text-lg font-semibold border-t border-slate-700/50 pt-2">
                  <span className="text-white">Total</span>
                  <span className="font-mono text-green-400">₹{total.toLocaleString()}</span>
                </div>
              </div>
              <p className="mt-4 text-xs text-slate-500">
                Your seats are held for 5 minutes. Complete payment before the timer expires.
              </p>
            </div>
          </div>
        </div>
      </main>
  );
}