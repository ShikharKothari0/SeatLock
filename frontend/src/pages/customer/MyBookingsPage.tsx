import { customerMessage } from '../../lib/customerMessage';
import { useState } from 'react';
import { useQuery } from '@tanstack/react-query';
import { apiClient } from '../../lib/api';
import { Link } from 'react-router-dom';
import { useSessionStore } from '../../store/sessionStore';
import { StatusBadge } from '../../components/common/StatusBadge';

export function MyBookingsPage() {
  const userId = useSessionStore(state => state.userId);
  const [page, setPage] = useState(0);
  const query = useQuery({ queryKey: ['bookings', userId, page], queryFn: () => apiClient.getBookings(userId, page), retry: false });
  const receipts = query.data?.items ?? [];
  return <main className="max-w-4xl w-full mx-auto px-4 py-8 space-y-6">
    <h1 className="text-3xl font-bold text-slate-900 dark:text-white">My Bookings</h1>
    <p className="text-slate-600 dark:text-slate-400">Find your upcoming plans and past bookings here.</p>
    {query.isLoading && <p role="status" className="text-slate-500">Loading bookings…</p>}
    {query.error && <div role="alert" className="text-red-600"><p>{customerMessage(query.error, 'We couldn’t load your bookings. Please try again.')}</p><button onClick={() => void query.refetch()}>Try again</button></div>}
    {query.data && !receipts.length && <p className="text-slate-600 dark:text-slate-400">No bookings yet. <Link to="/" className="text-indigo-700 dark:text-indigo-400">Browse events</Link></p>}
    {receipts.map(({ booking, event, seats }) => <article key={booking.id} className="bg-slate-100/50 dark:bg-slate-800/50 border border-slate-300/50 dark:border-slate-600/50 rounded-xl p-6 space-y-3">
      <h2 className="text-xl text-slate-900 dark:text-white">{event?.name ?? booking.eventId}</h2>
      <StatusBadge status={booking.status} />
      <p className="text-slate-700 dark:text-slate-300">{booking.seatIds.length} seat(s): {booking.seatIds.map(id => seats.find(seat => seat.id === id)?.seatNumber ?? id).join(', ')}</p>
      <p className="text-slate-600 dark:text-slate-400">{new Date(booking.createdAt).toLocaleString('en-IN')}</p>
      <Link to={`/booking-confirmed/${booking.id}`} className="inline-block text-indigo-700 dark:text-indigo-400">View booking details</Link>
    </article>)}
    {query.data && <div className="flex gap-4 text-indigo-700 dark:text-indigo-400">
      <button disabled={page === 0} onClick={() => setPage(page - 1)}>Previous</button>
      <span>Page {page + 1} of {Math.max(1, query.data.totalPages)}</span>
      <button disabled={page + 1 >= query.data.totalPages} onClick={() => setPage(page + 1)}>Next</button>
    </div>}
  </main>;
}
