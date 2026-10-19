import { useState } from 'react';
import type { EventResponse } from '../../types/api';

export function EventPoster({ url, name }: { url?: string; name: string }) {
  const [failed, setFailed] = useState(false);
  if (!/^https:\/\//i.test(url ?? '') || failed) return <div className="h-40 rounded-lg bg-indigo-100 dark:bg-indigo-950 flex items-center justify-center text-indigo-700 dark:text-indigo-200" aria-label={`${name} poster unavailable`}>Live experiences · SeatLock</div>;
  return <img src={url} alt={`${name} poster`} loading="lazy" referrerPolicy="no-referrer" onError={() => setFailed(true)} className="w-full h-48 object-cover rounded-lg" />;
}
export function EventDetails({ event }: { event: EventResponse }) {
  return <section aria-label="Event details" className="space-y-3 text-slate-700 dark:text-slate-300">
    <EventPoster key={event.posterUrl} url={event.posterUrl} name={event.name} />
    <p>{event.category ?? 'Other'}{event.durationMinutes ? ` · ${event.durationMinutes} minutes` : ''}</p>
    <p>{event.venueName}, {event.venueCity} · {new Date(event.eventTime).toLocaleString()}</p>
    {event.description && <p className="whitespace-pre-wrap break-words">{event.description}</p>}
  </section>;
}
