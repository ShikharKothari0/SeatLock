// SelectionSummary - Lists selected seats with pricing and checkout button

import type { SeatResponse } from '../../types/api';
import { PRICE_PER_SEAT, PLATFORM_FEE, TAXES } from '../../lib/constants';
import { StatusBadge } from '../../components/common/StatusBadge';

interface SelectionSummaryProps {
  selectedSeats: SeatResponse[];
  pricePerSeat?: number;
}

const seatPrice = PRICE_PER_SEAT;
const platformFee = PLATFORM_FEE;
const taxes = TAXES;

export function SelectionSummary({ selectedSeats, pricePerSeat = seatPrice }: SelectionSummaryProps) {
  const seatCount = selectedSeats.length;
  const subtotal = seatCount * pricePerSeat;
  const total = subtotal + platformFee + taxes;

  if (seatCount === 0) {
    return (
      <div className="bg-slate-800/50 border border-slate-600/50 rounded-xl p-5">
        <div className="text-center py-8">
          <StatusBadge status="INFO" label="No seats selected" className="mb-3" />
          <p className="text-slate-400 text-sm">
            Click on available seats to add them to your selection
          </p>
        </div>
      </div>
    );
  }

  return (
    <div className="bg-slate-800/50 border border-slate-600/50 rounded-xl p-5 space-y-4">
      <h3 className="text-lg font-semibold text-white">Selection Summary</h3>

      {/* Seat list */}
      <div className="space-y-2 max-h-48 overflow-y-auto pr-1">
        {selectedSeats.map((seat) => (
          <div
            key={seat.id}
            className="flex items-center justify-between py-2 px-3 bg-slate-900/50 rounded-lg border border-slate-700/50"
          >
            <div className="flex items-center gap-3">
              <span className="w-8 h-8 rounded-full bg-violet-500 ring-2 ring-violet-400 ring-offset-2 ring-offset-slate-900 flex items-center justify-center">
                <span className="text-[10px] font-mono font-bold text-white">
                  {seat.seatNumber.slice(-2).padStart(2, '0').slice(-1)}
                </span>
              </span>
              <span className="font-mono text-sm text-white">
                {seat.seatNumber}
              </span>
              <span className="text-slate-500 text-sm">({seat.section})</span>
            </div>
            <span className="font-mono text-sm text-green-400">
              ₹{pricePerSeat.toLocaleString()}
            </span>
          </div>
        ))}
      </div>

      {/* Price breakdown */}
      <div className="border-t border-slate-700/50 pt-4 space-y-2">
        <div className="flex justify-between text-sm">
          <span className="text-slate-400">{seatCount} seat{seatCount > 1 ? 's' : ''} × ₹{pricePerSeat.toLocaleString()}</span>
          <span className="font-mono text-white">₹{subtotal.toLocaleString()}</span>
        </div>
        <div className="flex justify-between text-sm">
          <span className="text-slate-400">Platform fee</span>
          <span className="font-mono text-white">₹{platformFee.toLocaleString()}</span>
        </div>
        <div className="flex justify-between text-sm">
          <span className="text-slate-400">Taxes</span>
          <span className="font-mono text-white">₹{taxes.toLocaleString()}</span>
        </div>
        <div className="flex justify-between text-lg font-semibold border-t border-slate-700/50 pt-2">
          <span className="text-white">Total</span>
          <span className="font-mono text-green-400">₹{total.toLocaleString()}</span>
        </div>
      </div>

      {/* Checkout button */}
      <button
        className="w-full px-4 py-3 text-sm font-medium text-white bg-indigo-600 hover:bg-indigo-500 rounded-lg transition-colors disabled:opacity-50 disabled:cursor-not-allowed"
        disabled={seatCount === 0}
      >
        Proceed to Checkout
      </button>
    </div>
  );
}