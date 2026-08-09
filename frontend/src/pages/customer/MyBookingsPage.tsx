// MyBookingsPage - Shows user's booking history

import { useNavigate } from 'react-router-dom';
import { useSessionStore } from '../../store/sessionStore';
import { StatusBadge } from '../../components/common/StatusBadge';
import { Ticket, Calendar, MapPin, ArrowRight } from 'lucide-react';

export function MyBookingsPage() {
  const navigate = useNavigate();
  const { lastBookingId } = useSessionStore();

  // In a real app, this would come from an API
  const bookings = lastBookingId ? [
    {
      id: lastBookingId,
      eventId: '22222222-2222-2222-2222-222222222222',
      eventName: 'Test Concert',
      venue: 'Test Arena, Mumbai',
      date: '2026-08-05T13:08:02Z',
      status: 'CONFIRMED' as const,
      seatCount: 1,
    }
  ] : [];

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

  return (
    <main className="flex-1 max-w-7xl w-full mx-auto px-4 sm:px-6 lg:px-8 py-8">
      <div className="mb-6">
          <h1 className="text-3xl font-bold text-white mb-2">My Bookings</h1>
          <p className="text-slate-400">View and manage your bookings</p>
        </div>

        {bookings.length === 0 ? (
          <div className="bg-slate-800/50 border border-slate-600/50 rounded-xl p-12 text-center">
            <Ticket className="w-16 h-16 mx-auto mb-4 text-slate-600" aria-hidden="true" />
            <h2 className="text-xl font-semibold text-white mb-2">No bookings yet</h2>
            <p className="text-slate-400 mb-6">Browse events and book your first seats</p>
            <button
              onClick={() => navigate('/')}
              className="px-6 py-3 text-sm font-medium text-white bg-indigo-600 hover:bg-indigo-500 rounded-lg transition-colors"
            >
              Browse Events
            </button>
          </div>
        ) : (
          <div className="space-y-4">
            {bookings.map((booking) => (
              <article
                key={booking.id}
                className="bg-slate-800/50 border border-slate-600/50 rounded-xl overflow-hidden hover:border-slate-500/50 transition-colors"
              >
                <div className="p-6">
                  <div className="flex flex-col sm:flex-row sm:items-center justify-between gap-4">
                    <div className="flex items-center gap-4">
                      <div className="w-16 h-16 rounded-lg bg-indigo-500/20 flex items-center justify-center flex-shrink-0">
                        <Ticket className="w-8 h-8 text-indigo-400" aria-hidden="true" />
                      </div>
                      <div>
                        <h3 className="text-lg font-semibold text-white">{booking.eventName}</h3>
                        <div className="flex flex-wrap items-center gap-4 mt-1 text-sm text-slate-400">
                          <span className="flex items-center gap-1">
                            <Calendar className="w-4 h-4" aria-hidden="true" />
                            {formatDate(booking.date)}
                          </span>
                          <span className="flex items-center gap-1">
                            <MapPin className="w-4 h-4" aria-hidden="true" />
                            {booking.venue}
                          </span>
                          <span className="font-mono text-slate-500">{booking.seatCount} seat{booking.seatCount > 1 ? 's' : ''}</span>
                        </div>
                      </div>
                    </div>
                    <div className="flex items-center gap-3">
                      <StatusBadge status={booking.status} />
                      <button
                        onClick={() => navigate(`/booking-confirmed/${booking.id}`)}
                        className="px-4 py-2 text-sm font-medium text-slate-300 hover:text-white bg-slate-800/50 border border-slate-600/50 rounded-lg transition-colors flex items-center gap-1"
                      >
                        View Details
                        <ArrowRight className="w-4 h-4" aria-hidden="true" />
                      </button>
                    </div>
                  </div>
                </div>
              </article>
            ))}
          </div>
        )}
      </main>
  );
}