// BookingConfirmedPage - Shows booking confirmation details

import { useParams, useNavigate } from 'react-router-dom';
import { useEffect, useState } from 'react';
import { useSessionStore } from '../../store/sessionStore';
import { LoadingSpinner } from '../../components/common/LoadingSpinner';
import { StatusBadge } from '../../components/common/StatusBadge';
import { CheckCircle, Ticket, Calendar, MapPin, CreditCard } from 'lucide-react';

export function BookingConfirmedPage() {
  const { bookingId } = useParams<{ bookingId: string }>();
  const navigate = useNavigate();
  const { lastBookingId, reset } = useSessionStore();
  const [booking, setBooking] = useState<{
    id: string;
    eventId: string;
    seatIds: string[];
    createdAt: string;
    status: string;
  } | null>(null);
  const [isLoading, setIsLoading] = useState(true);

  useEffect(() => {
    // In a real app, we'd fetch booking details from an API
    // For now, use the session store data
    if (lastBookingId && bookingId === lastBookingId) {
      setBooking({
        id: lastBookingId,
        eventId: '22222222-2222-2222-2222-222222222222',
        seatIds: [], // Would come from API
        createdAt: new Date().toISOString(),
        status: 'CONFIRMED',
      });
      setIsLoading(false);
    } else if (bookingId) {
      // Try to fetch booking details
      // For demo, just use the bookingId from URL
      setBooking({
        id: bookingId,
        eventId: '22222222-2222-2222-2222-222222222222',
        seatIds: [],
        createdAt: new Date().toISOString(),
        status: 'CONFIRMED',
      });
      setIsLoading(false);
    } else {
      navigate('/');
    }
  }, [bookingId, lastBookingId, navigate]);

  const formatDate = (isoString: string) => {
    const date = new Date(isoString);
    return date.toLocaleDateString('en-IN', {
      weekday: 'long',
      year: 'numeric',
      month: 'long',
      day: 'numeric',
      hour: '2-digit',
      minute: '2-digit',
    });
  };

  if (isLoading) {
    return (
      <div className="flex flex-col items-center justify-center min-h-[60vh] gap-4">
        <LoadingSpinner size="lg" ariaLabel="Loading confirmation" />
        <p className="text-slate-400 text-sm">Loading confirmation...</p>
      </div>
    );
  }

  if (!booking) {
    return (
      <div className="flex flex-col items-center justify-center min-h-[60vh] gap-4 text-center">
        <StatusBadge status="FAILED" label="Booking not found" />
        <p className="text-slate-400">No booking details available</p>
        <button
          onClick={() => navigate('/')}
          className="mt-4 px-4 py-2 text-sm font-medium text-white bg-indigo-600 hover:bg-indigo-500 rounded-lg transition-colors"
        >
          Back to Home
        </button>
      </div>
    );
  }

  return (
    <main className="flex-1 max-w-7xl w-full mx-auto px-4 sm:px-6 lg:px-8 py-8">
      <div className="max-w-2xl mx-auto">
          {/* Success Animation */}
          <div className="text-center mb-8">
            <div className="w-24 h-24 mx-auto mb-6 rounded-full bg-green-500/20 flex items-center justify-center">
              <CheckCircle className="w-12 h-12 text-green-500" aria-hidden="true" />
            </div>
            <h1 className="text-3xl font-bold text-white mb-2">Booking Confirmed!</h1>
            <p className="text-slate-400">Your seats have been successfully booked</p>
          </div>

          {/* Booking Details Card */}
          <div className="bg-slate-800/50 border border-slate-600/50 rounded-xl p-6 mb-6">
            <div className="flex items-center gap-3 mb-4 p-3 bg-green-900/30 border border-green-700/50 rounded-lg">
              <Ticket className="w-6 h-6 text-green-400 flex-shrink-0" aria-hidden="true" />
              <div>
                <p className="text-xs text-slate-400 uppercase tracking-wide">Booking ID</p>
                <p className="font-mono text-white text-sm">{booking.id}</p>
              </div>
            </div>

            <div className="space-y-4">
              <div className="flex items-center gap-3 p-3 bg-slate-900/50 rounded-lg">
                <Calendar className="w-5 h-5 text-indigo-400 flex-shrink-0" aria-hidden="true" />
                <div>
                  <p className="text-xs text-slate-400">Booked On</p>
                  <p className="font-mono text-white text-sm">{formatDate(booking.createdAt)}</p>
                </div>
              </div>

              <div className="flex items-center gap-3 p-3 bg-slate-900/50 rounded-lg">
                <MapPin className="w-5 h-5 text-indigo-400 flex-shrink-0" aria-hidden="true" />
                <div>
                  <p className="text-xs text-slate-400">Event</p>
                  <p className="font-mono text-white text-sm">Test Concert - Test Arena, Mumbai</p>
                </div>
              </div>

              <div className="flex items-center gap-3 p-3 bg-slate-900/50 rounded-lg">
                <CreditCard className="w-5 h-5 text-indigo-400 flex-shrink-0" aria-hidden="true" />
                <div>
                  <p className="text-xs text-slate-400">Payment Status</p>
                  <p className="font-mono text-green-400 text-sm">Paid (Demo)</p>
                </div>
              </div>
            </div>

            {/* Seats */}
            <div className="mt-6 pt-6 border-t border-slate-700/50">
              <p className="text-xs text-slate-400 uppercase tracking-wide mb-3">Your Seats</p>
              <div className="flex flex-wrap gap-2">
                {booking.seatIds.length > 0 ? (
                  booking.seatIds.map((seatId) => (
                    <span
                      key={seatId}
                      className="px-3 py-1.5 bg-violet-500/20 border border-violet-500/50 rounded-full text-violet-300 font-mono text-sm"
                    >
                      {seatId.slice(-8)}
                    </span>
                  ))
                ) : (
                  <span className="px-3 py-1.5 bg-slate-700/50 border border-slate-600/50 rounded-full text-slate-400 font-mono text-sm">
                    Seat details loaded from session
                  </span>
                )}
              </div>
            </div>
          </div>

          {/* Action Buttons */}
          <div className="grid grid-cols-1 sm:grid-cols-2 gap-4">
            <button
              onClick={() => {
                reset();
                navigate('/my-bookings');
              }}
              className="px-4 py-3 text-sm font-medium text-white bg-indigo-600 hover:bg-indigo-500 rounded-lg transition-colors"
            >
              <Ticket className="w-4 h-4 inline-block mr-2" aria-hidden="true" />
              View My Bookings
            </button>
            <button
              onClick={() => {
                reset();
                navigate('/');
              }}
              className="px-4 py-3 text-sm font-medium text-slate-300 hover:text-white bg-slate-800/50 border border-slate-600/50 rounded-lg transition-colors"
            >
              Book More Seats
            </button>
          </div>

          {/* Helpful Info */}
          <div className="mt-8 p-4 bg-slate-800/50 border border-slate-600/50 rounded-lg">
            <p className="text-sm text-slate-400 text-center">
              A confirmation email has been sent to your registered email address.
              Please bring a valid ID and this booking reference to the venue.
            </p>
          </div>
        </div>
      </main>
  );
}