import { useEffect, useState } from 'react';
import { EventPoster } from '../../components/customer/EventDetails';
import { filterEvents, salesMessage } from '../../lib/eventDiscovery';
// HomePage - Displays events with Book Now buttons

import { useQuery } from '@tanstack/react-query';
import { apiClient } from '../../lib/api';
import { useNavigate, useSearchParams } from 'react-router-dom';
import type { EventResponse } from '../../types/api';
import { LoadingSpinner } from '../../components/common/LoadingSpinner';
import { StatusBadge } from '../../components/common/StatusBadge';

export function HomePage() {
  const navigate = useNavigate();
  const [filters, setFilters] = useSearchParams();
  const [now, setNow] = useState(Date.now());
  useEffect(() => { const timer = setInterval(() => setNow(Date.now()), 30000); return () => clearInterval(timer); }, []);
  function setFilter(key: string, value: string) { const next = new URLSearchParams(filters); if (value) next.set(key,value); else next.delete(key); setFilters(next, { replace: true }); }


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
        <p className="text-slate-600 dark:text-slate-400 text-sm">Loading events...</p>
      </div>
    );
  }

  if (error) {
    return (
      <div className="flex flex-col items-center justify-center min-h-[60vh] gap-4 text-center">
        <StatusBadge status="FAILED" label="Failed to load events" />
        <p className="text-slate-600 dark:text-slate-400 text-sm max-w-xs">
          We couldn’t load events. Please try again.
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
        <p className="text-slate-600 dark:text-slate-400 text-sm">No events are currently available.</p>
      </div>
    );
  }

  const visible = filterEvents(events, filters, now);
  const filterField = 'block mt-1 w-full rounded border p-2 bg-white dark:bg-slate-900';
  return (
    <main className="max-w-7xl mx-auto px-4 py-8 space-y-8">
      {/* Hero Section */}
      <section className="text-center py-12">
        <h1 className="text-4xl md:text-5xl font-bold text-slate-900 dark:text-white mb-4">
          Find your event. Choose your seats.
        </h1>
        <p className="text-lg text-slate-600 dark:text-slate-400 max-w-2xl mx-auto">
          Discover events, choose your seats and plan your next outing.
          Demo bookings — no money will be charged.
        </p>
      </section>

      <section aria-label="Find events" className="rounded-xl border p-4 space-y-4 text-slate-700 dark:text-slate-300">
        <div className="grid sm:grid-cols-2 lg:grid-cols-3 gap-4">
          <label>Search events<input className={filterField} type="search" value={filters.get('q') ?? ''} onChange={e=>setFilter('q',e.target.value)} placeholder="Event or venue name" /></label>
          <label>City<select aria-label="City" className={filterField} value={filters.get('city') ?? ''} onChange={e=>setFilter('city',e.target.value)}><option value="">All cities</option>{[...new Set(events.map(e=>e.venueCity))].sort().map(city=><option key={city}>{city}</option>)}</select></label>
          <label>Category<select aria-label="Category" className={filterField} value={filters.get('category') ?? ''} onChange={e=>setFilter('category',e.target.value)}><option value="">All categories</option>{['Music','Comedy','Theatre','Sports','Workshop','Other'].map(category=><option key={category}>{category}</option>)}</select></label>
          <label>From date<input className={filterField} type="date" value={filters.get('from') ?? ''} onChange={e=>setFilter('from',e.target.value)} /></label>
          <label>To date<input className={filterField} type="date" min={filters.get('from') ?? undefined} value={filters.get('to') ?? ''} onChange={e=>setFilter('to',e.target.value)} /></label>
        </div>
        <label className="flex gap-2 items-center"><input type="checkbox" checked={filters.get('past') === '1'} onChange={e=>setFilter('past',e.target.checked?'1':'')} />Include past events</label>
        <p className="text-sm">Dates use your local timezone: {Intl.DateTimeFormat().resolvedOptions().timeZone}.</p>
        <button onClick={()=>setFilters({})} className="text-indigo-700 dark:text-indigo-300">Clear filters</button>
        <p role="status">{visible.length} event(s) found</p>
        {filters.get('from') && filters.get('to') && filters.get('from')! > filters.get('to')! && <p role="alert">Choose an end date on or after the start date.</p>}
      </section>

      {/* Event Cards */}
      <section>
        <h2 className="text-2xl font-bold text-slate-900 dark:text-white mb-6">{filters.get('past') === '1' ? 'Events' : 'Upcoming Events'}</h2>
        {!visible.length && <p className="text-slate-600 dark:text-slate-400">No events match your filters. Try another city, category or date.</p>}
        <div className="grid grid-cols-1 md:grid-cols-2 lg:grid-cols-3 gap-6">
          {visible.map((event) => (
            <EventCard key={event.id} event={event} onBookNow={handleBookNow} now={now} />
          ))}
        </div>
      </section>
    </main>
  );
}

interface EventCardProps {
  event: EventResponse;
  now: number;
  onBookNow: (eventId: string) => void;
}

function EventCard({ event, onBookNow, now }: EventCardProps) {
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
    <article className="bg-slate-100/50 dark:bg-slate-800/50 border border-slate-300/50 dark:border-slate-600/50 rounded-xl p-6 hover:border-slate-500/50 transition-colors flex flex-col">
      <EventPoster key={event.posterUrl} url={event.posterUrl} name={event.name} />
      <p className="text-sm text-indigo-700 dark:text-indigo-300 mt-4">{event.category ?? 'Other'}{event.durationMinutes ? ` · ${event.durationMinutes} minutes` : ''}</p>
      <div className="flex-1 mt-2">
        <h3 className="text-xl font-bold text-slate-900 dark:text-white mb-2">{event.name}</h3>
        <div className="space-y-2 text-sm text-slate-600 dark:text-slate-400">
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
      {salesMessage(event,now) && <p className="mt-4 text-sm text-slate-600 dark:text-slate-400">{salesMessage(event,now)}</p>}
      <button
        onClick={() => onBookNow(event.id)}
        className="mt-6 w-full px-4 py-3 text-sm font-medium text-white bg-indigo-600 hover:bg-indigo-500 rounded-lg transition-colors"
      >
        {salesMessage(event,now) ? 'View event' : 'Book Now'}
      </button>
    </article>
  );
}
