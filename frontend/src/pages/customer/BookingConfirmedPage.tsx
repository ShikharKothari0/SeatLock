import { customerMessage } from '../../lib/customerMessage';
import { PriceBreakdown } from '../../components/customer/PriceBreakdown';
import { useQuery } from '@tanstack/react-query';
import { apiClient } from '../../lib/api';
import { Link, useParams } from 'react-router-dom';
import { useSessionStore } from '../../store/sessionStore';
import { StatusBadge } from '../../components/common/StatusBadge';

export function BookingConfirmedPage() {
  const { bookingId } = useParams();
  const userId = useSessionStore(state => state.userId);
  const query = useQuery({ queryKey: ['booking', bookingId, userId], queryFn: () => apiClient.getBooking(bookingId!, userId), enabled: !!bookingId, retry: false });
  const price = useQuery({ queryKey: ['booking-price', bookingId, userId], queryFn: () => apiClient.bookingPrice(bookingId!), enabled: !!bookingId, retry: false });
  if (query.isLoading) return <p role="status" className="p-8 text-slate-500">Loading your booking…</p>;
  const receipt = query.data;
  if (!receipt) return <main className="max-w-3xl mx-auto p-8 space-y-4">
    <h1 className="text-2xl text-slate-900 dark:text-white">Booking details unavailable</h1>
    <p className="text-slate-600 dark:text-slate-400">{customerMessage(query.error, 'We couldn’t find this booking. Check My Bookings or try again.')}</p>
    <button className="block text-indigo-600" onClick={() => void query.refetch()}>Try again</button>
    <Link to="/my-bookings" className="text-indigo-700 dark:text-indigo-400">View bookings</Link>
  </main>;
  const { booking, event, seats } = receipt;
  return <main className="max-w-3xl w-full mx-auto px-4 py-8 space-y-6">
    <h1 className="text-3xl font-bold text-slate-900 dark:text-white">Booking {booking.status === 'CONFIRMED' ? 'confirmed' : 'receipt'}</h1>
    <StatusBadge status={booking.status} />
    {price.data ? <PriceBreakdown quote={price.data} /> : <p>Price details are unavailable for this booking.</p>}
    <dl className="bg-slate-100/50 dark:bg-slate-800/50 rounded-xl p-6 space-y-4 text-slate-700 dark:text-slate-300">
      <div><dt>Booking reference</dt><dd className="font-mono break-all text-slate-900 dark:text-white">{booking.id}</dd></div>
      <div><dt>Event</dt><dd>{event?.name ?? booking.eventId}</dd></div>
      {event && <div><dt>Venue</dt><dd>{event.venueName}, {event.venueCity}</dd></div>}
      <div><dt>Booked at</dt><dd>{new Date(booking.createdAt).toLocaleString('en-IN')}</dd></div>
      <div><dt>Seats ({booking.seatIds.length})</dt><dd>{booking.seatIds.map(id => seats.find(seat => seat.id === id)?.seatNumber ?? id).join(', ')}</dd></div>
    </dl>
    <p className="text-sm text-slate-600 dark:text-slate-400">Demo booking — no money was charged.</p>
    <div className="flex gap-6"><Link to="/my-bookings" className="text-indigo-700 dark:text-indigo-400">My Bookings</Link><Link to="/" className="text-indigo-700 dark:text-indigo-400">Browse events</Link></div>
  </main>;
}
