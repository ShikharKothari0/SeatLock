// HomePage - Displays events with Book Now buttons

import { useQuery } from '@tanstack/react-query';
import { apiClient } from '../../lib/api';
import { useNavigate } from 'react-router-dom';
import type { EventResponse } from '../../types/api';
import { LoadingSpinner } from '../../components/common/LoadingSpinner';
import { StatusBadge } from '../../components/common/StatusBadge';

export function HomePage() {
  const navigate = useNavigate();

  const { data: events, isLoading, error, refetch } = useQuery<EventResponse[], Error>({
    queryKey: ['events'],
    queryFn: () => apiClient.getEvents(),
    staleTime: 30_000,
    retry: 3,
    retryDelay: (attemptIndex) => Math.min(1000 * 2 ** attemptIndex, 30000),
  });

  const handleBookNow = (eventId: string) => {
    navigate(`/events/${eventId}`);
  };

  if (isLoading) {
    return (
      <div className="flex flex-col items-center justify-center min-h-[60vh] gap-4">
        <LoadingSpinner size="lg" ariaLabel="Loading events" />
        <p className="text-slate-400 text-sm">Loading events...</p>
      </div>
    );
  }

  if (error) {
    return (
      <div className="flex flex-col items-center justify-center min-h-[60vh] gap-4 text-center">
        <StatusBadge status="FAILED" label="Failed to load events" />
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

  if (!events || events.length === 0) {
    return (
      <div className="flex flex-col items-center justify-center min-h-[60vh] gap-4 text-center">
        <StatusBadge status="INFO" label="No events found" />
        <p className="text-slate-400 text-sm">No events are currently available.</p>
      </div>
    );
  }

  return (
    <div className="space-y-8">
      {/* Hero Section */}
      <section className="text-center py-12">
        <h1 className="text-4xl md:text-5xl font-bold text-white mb-4">
          Preventing Seat Overselling Through Distributed Locking
        </h1>
        <p className="text-lg text-slate-400 max-w-2xl mx-auto">
          Real-time seat availability with 5-minute hold guarantees.
          Powered by Redis distributed locks and PostgreSQL.
        </p>
      </section>

      {/* Quick Stats */}
      <section className="grid grid-cols-1 md:grid-cols-2 gap-4 max-w-4xl mx-auto">
        <div className="bg-slate-800/50 border border-slate-600/50 rounded-xl p-4">
          <p className="text-slate-400 text-sm uppercase tracking-wide mb-1">Events Available</p>
          <p className="font-mono text-3xl font-bold text-white">{events.length}</p>
        </div>
        <div className="bg-slate-800/50 border border-slate-600/50 rounded-xl p-4">
          <p className="text-slate-400 text-sm uppercase tracking-wide mb-1">Active Locks</p>
          <p className="font-mono text-3xl font-bold text-white">—</p>
        </div>
      </section>

      {/* Event Cards */}
      <section>
        <h2 className="text-2xl font-bold text-white mb-6">Upcoming Events</h2>
        <div className="grid grid-cols-1 md:grid-cols-2 lg:grid-cols-3 gap-6">
          {events.map((event) => (
            <EventCard key={event.id} event={event} onBookNow={handleBookNow} />
          ))}
        </div>
      </section>
    </div>
  );
}

interface EventCardProps {
  event: EventResponse;
  onBookNow: (eventId: string) => void;
}

function EventCard({ event, onBookNow }: EventCardProps) {
  const formatDate = (isoString: string) => {
    const date = new Date(isoString);
    return date.toLocaleDateString('en-IN', {
      weekday: 'short',
      year: 'numeric',
      month: 'short',
      day: 'numeric',
      hour: '2-digit',
      minute: '2-digit',
    });
  };

  return (
    <article className="bg-slate-800/50 border border-slate-600/50 rounded-xl p-6 hover:border-slate-500/50 transition-colors flex flex-col">
      <div className="flex-1">
        <h3 className="text-xl font-bold text-white mb-2">{event.name}</h3>
        <div className="space-y-2 text-sm text-slate-400">
          <div className="flex items-center gap-2">
            <svg className="w-4 h-4 text-slate-500 flex-shrink-0" fill="none" stroke="currentColor" viewBox="0 0 24 24" aria-hidden="true">
              <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M17.657 16.657L13.414 20.9a1.998 1.998 0 01-2.827 0l-4.244-4.243a8 8 0 1111.314 0z" />
              <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M15 11a3 3 0 11-6 0 3 3 0 016 0z" />
            </svg>
            <span>{event.venueName}, {event.venueCity}</span>
          </div>
          <div className="flex items-center gap-2">
            <svg className="w-4 h-4 text-slate-500 flex-shrink-0" fill="none" stroke="currentColor" viewBox="0 0 24 24" aria-hidden="true">
              <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M8 7V3m8 4V3m-9 8h10M5 21h14a2 2 0 002-2V7a2 2 0 00-2-2H5a2 2 0 00-2 2v12a2 2 0 002 2z" />
            </svg>
            <span>{formatDate(event.eventTime)}</span>
          </div>
          <div className="flex items-center gap-2">
            <svg className="w-4 h-4 text-slate-500 flex-shrink-0" fill="none" stroke="currentColor" viewBox="0 0 24 24" aria-hidden="true">
              <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M12 8v4l3 3m6-3a9 9 0 11-18 0 9 9 0 0118 0z" />
            </svg>
            <span>Sale starts: {formatDate(event.saleStartTime)}</span>
          </div>
        </div>
      </div>
      <button
        onClick={() => onBookNow(event.id)}
        className="mt-6 w-full px-4 py-3 text-sm font-medium text-white bg-indigo-600 hover:bg-indigo-500 rounded-lg transition-colors"
      >
        Book Now
      </button>
    </article>
  );
}