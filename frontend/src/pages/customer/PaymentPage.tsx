// PaymentPage - Payment selection and booking confirmation

import { useNavigate } from 'react-router-dom';
import { useState } from 'react';
import { useSessionStore } from '../../store/sessionStore';
import { useBookingConfirm } from '../../hooks/useBookingConfirm';
import { LoadingSpinner } from '../../components/common/LoadingSpinner';
import { HoldTimer } from '../../components/customer/HoldTimer';
import { PRICE_PER_SEAT, PLATFORM_FEE, TAXES } from '../../lib/constants';
import { CreditCard, Smartphone, Building2 } from 'lucide-react';

export function PaymentPage() {
  const navigate = useNavigate();
  const { userId, heldSeatIds, holdExpiresAt, idempotencyKey, setBookingConfirmed, clearHold } =
    useSessionStore();

  const [selectedPaymentMethod, setSelectedPaymentMethod] = useState<'upi' | 'card' | 'netbanking'>('upi');
  const [isProcessing, setIsProcessing] = useState(false);
  const [error, setError] = useState<string | null>(null);

  const confirmBookingMutation = useBookingConfirm({
    onSuccess: (response) => {
      setBookingConfirmed(response.id);
      setIsProcessing(false);
      navigate(`/booking-confirmed/${response.id}`);
    },
    onError: (err) => {
      setIsProcessing(false);
      if ((err as Error & { status?: number }).status === 409) {
        setError('Your hold has expired. Please go back and select seats again.');
        // Clear hold state and redirect after delay
        setTimeout(() => {
          clearHold();
          navigate('/');
        }, 3000);
      } else {
        setError(err.message || 'Payment failed. Please try again.');
      }
    },
  });

  // If no holds, redirect to home
  if (heldSeatIds.length === 0) {
    return null; // Will be handled by useEffect in CheckoutPage
  }

  const seatCount = heldSeatIds.length;
  const subtotal = seatCount * PRICE_PER_SEAT;
  const total = subtotal + PLATFORM_FEE + TAXES;

  const handleConfirmBooking = async () => {
    if (!idempotencyKey) return;

    setIsProcessing(true);
    setError(null);

    confirmBookingMutation.mutate({
      userId,
      seatIds: heldSeatIds,
      idempotencyKey,
    });
  };

  const handleHoldExpiry = () => {
    clearHold();
    navigate('/');
  };

  return (
    <main className="flex-1 max-w-7xl w-full mx-auto px-4 sm:px-6 lg:px-8 py-8">
      {/* Header */}
      <div className="mb-6 flex items-center justify-between">
          <div>
            <h1 className="text-3xl font-bold text-white mb-2">Payment</h1>
            <p className="text-slate-400">Choose your payment method to confirm the booking</p>
          </div>
        </div>

        <div className="grid grid-cols-1 lg:grid-cols-3 gap-6">
          {/* Payment Form */}
          <div className="lg:col-span-2 space-y-6">
            {/* Payment Methods */}
            <div className="bg-slate-800/50 border border-slate-600/50 rounded-xl p-5">
              <h3 className="text-lg font-semibold text-white mb-4">Payment Method</h3>
              <div className="grid grid-cols-1 sm:grid-cols-3 gap-3">
                {[
                  { id: 'upi', label: 'UPI', icon: Smartphone, description: 'PhonePe, Google Pay, Paytm' },
                  { id: 'card', label: 'Credit/Debit Card', icon: CreditCard, description: 'Visa, Mastercard, RuPay' },
                  { id: 'netbanking', label: 'Net Banking', icon: Building2, description: '50+ banks supported' },
                ].map((method) => (
                  <button
                    key={method.id}
                    type="button"
                    onClick={() => setSelectedPaymentMethod(method.id as typeof selectedPaymentMethod)}
                    className={`relative p-4 rounded-lg border-2 transition-all text-left ${
                      selectedPaymentMethod === method.id
                        ? 'border-indigo-500 bg-indigo-500/10'
                        : 'border-slate-700/50 hover:border-slate-600/50'
                    }`}
                  >
                    <div className="flex items-center gap-3">
                      <method.icon className="w-6 h-6 text-slate-400" aria-hidden="true" />
                      <div>
                        <p className="font-medium text-white">{method.label}</p>
                        <p className="text-xs text-slate-500">{method.description}</p>
                      </div>
                    </div>
                    {selectedPaymentMethod === method.id && (
                      <span className="absolute top-2 right-2 w-5 h-5 rounded-full bg-indigo-500 flex items-center justify-center">
                        <svg className="w-3 h-3 text-white" fill="currentColor" viewBox="0 0 20 20" aria-hidden="true">
                          <path fillRule="evenodd" d="M16.707 5.293a1 1 0 010 1.414l-8 8a1 1 0 01-1.414 0l-4-4a1 1 0 011.414-1.414L8 12.586l7.293-7.293a1 1 0 011.414 0z" clipRule="evenodd" />
                        </svg>
                      </span>
                    )}
                  </button>
                ))}
              </div>
            </div>

            {/* Payment Details (placeholder forms) */}
            <div className="bg-slate-800/50 border border-slate-600/50 rounded-xl p-5">
              <h3 className="text-lg font-semibold text-white mb-4">Payment Details</h3>
              {selectedPaymentMethod === 'upi' && (
                <div className="space-y-4">
                  <p className="text-slate-400 text-sm">
                    You will be redirected to your UPI app to complete the payment.
                  </p>
                  <div className="p-4 bg-slate-900/50 rounded-lg border border-slate-700/50 text-center">
                    <p className="font-mono text-white">UPI ID: user@pay</p>
                    <p className="text-xs text-slate-500 mt-1">Demo mode - no real payment processed</p>
                  </div>
                </div>
              )}
              {selectedPaymentMethod === 'card' && (
                <div className="space-y-4">
                  <div className="grid grid-cols-1 sm:grid-cols-2 gap-4">
                    <div>
                      <label className="block text-xs font-medium text-slate-400 mb-1">Card Number</label>
                      <input
                        type="text"
                        placeholder="4242 4242 4242 4242"
                        className="w-full px-3 py-2 bg-slate-900/50 border border-slate-700/50 rounded-lg text-white placeholder-slate-500 focus:border-indigo-500 focus:outline-none focus:ring-1 focus:ring-indigo-500"
                      />
                    </div>
                    <div>
                      <label className="block text-xs font-medium text-slate-400 mb-1">Expiry</label>
                      <input
                        type="text"
                        placeholder="MM/YY"
                        className="w-full px-3 py-2 bg-slate-900/50 border border-slate-700/50 rounded-lg text-white placeholder-slate-500 focus:border-indigo-500 focus:outline-none focus:ring-1 focus:ring-indigo-500"
                      />
                    </div>
                    <div>
                      <label className="block text-xs font-medium text-slate-400 mb-1">CVV</label>
                      <input
                        type="password"
                        placeholder="123"
                        className="w-full px-3 py-2 bg-slate-900/50 border border-slate-700/50 rounded-lg text-white placeholder-slate-500 focus:border-indigo-500 focus:outline-none focus:ring-1 focus:ring-indigo-500"
                      />
                    </div>
                    <div>
                      <label className="block text-xs font-medium text-slate-400 mb-1">Name on Card</label>
                      <input
                        type="text"
                        placeholder="John Doe"
                        className="w-full px-3 py-2 bg-slate-900/50 border border-slate-700/50 rounded-lg text-white placeholder-slate-500 focus:border-indigo-500 focus:outline-none focus:ring-1 focus:ring-indigo-500"
                      />
                    </div>
                  </div>
                </div>
              )}
              {selectedPaymentMethod === 'netbanking' && (
                <div className="space-y-4">
                  <p className="text-slate-400 text-sm">Select your bank to proceed with net banking</p>
                  <div className="grid grid-cols-2 sm:grid-cols-4 gap-3">
                    {['HDFC', 'ICICI', 'SBI', 'Axis', 'Kotak', 'Yes Bank', 'IDFC', 'PNB'].map((bank) => (
                      <button
                        key={bank}
                        type="button"
                        className="p-3 bg-slate-900/50 border border-slate-700/50 rounded-lg hover:border-indigo-500 transition-colors text-sm font-medium text-white"
                      >
                        {bank}
                      </button>
                    ))}
                  </div>
                </div>
              )}
            </div>

            {/* Hold Timer */}
            {holdExpiresAt && (
              <HoldTimer expiresAt={holdExpiresAt} onExpiry={handleHoldExpiry} />
            )}

            {/* Confirm Button */}
            <div className="space-y-4">
              <button
                onClick={handleConfirmBooking}
                disabled={isProcessing}
                className="w-full px-4 py-3 text-sm font-medium text-white bg-indigo-600 hover:bg-indigo-500 rounded-lg transition-colors disabled:opacity-50 disabled:cursor-not-allowed"
              >
                {isProcessing ? (
                  <span className="flex items-center justify-center gap-2">
                    <LoadingSpinner size="sm" />
                    Confirming Booking...
                  </span>
                ) : (
                  `Confirm & Pay ₹${total.toLocaleString()}`
                )}
              </button>

              {error && (
                <div className="p-3 bg-red-900/30 border border-red-700/50 rounded-lg text-red-400 text-sm">
                  {error}
                </div>
              )}

              <p className="text-xs text-slate-500 text-center">
                By confirming, you agree to the Terms of Service and Privacy Policy.
              </p>
            </div>
          </div>

          {/* Order Summary Sidebar */}
          <div className="lg:col-span-1">
            <div className="bg-slate-800/50 border border-slate-600/50 rounded-xl p-5 sticky top-24">
              <h3 className="text-lg font-semibold text-white mb-4">Order Summary</h3>
              <div className="space-y-2 text-sm">
                <div className="flex justify-between">
                  <span className="text-slate-400">{seatCount} seat{seatCount > 1 ? 's' : ''} × ₹{PRICE_PER_SEAT.toLocaleString()}</span>
                  <span className="font-mono text-white">₹{subtotal.toLocaleString()}</span>
                </div>
                <div className="flex justify-between">
                  <span className="text-slate-400">Platform fee</span>
                  <span className="font-mono text-white">₹{PLATFORM_FEE.toLocaleString()}</span>
                </div>
                <div className="flex justify-between">
                  <span className="text-slate-400">Taxes</span>
                  <span className="font-mono text-white">₹{TAXES.toLocaleString()}</span>
                </div>
                <div className="flex justify-between text-lg font-semibold border-t border-slate-700/50 pt-2">
                  <span className="text-white">Total</span>
                  <span className="font-mono text-green-400">₹{total.toLocaleString()}</span>
                </div>
              </div>
              <div className="mt-4 pt-4 border-t border-slate-700/50">
                <p className="text-xs text-slate-500">
                  {holdExpiresAt && (
                    <>
                      <HoldTimer expiresAt={holdExpiresAt} onExpiry={handleHoldExpiry} />
                    </>
                  )}
                </p>
              </div>
            </div>
          </div>
        </div>
      </main>
  );
}