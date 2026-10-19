import type { EventResponse } from '../types/api';

export function salesMessage(event: EventResponse, now = Date.now()): string | null {
  if (Date.parse(event.eventTime) <= now) return 'Past event — booking is closed.';
  if (event.publication === 'CLOSED' || Date.parse(event.saleEndTime ?? event.eventTime) <= now) return 'Ticket sales are closed.';
  if (Date.parse(event.saleStartTime) > now) return 'Tickets go on sale soon.';
  return null;
}
export function filterEvents(events: EventResponse[], filters: URLSearchParams, now = Date.now()) {
  const search = (filters.get('q') ?? '').trim().toLocaleLowerCase();
  return events.filter(event => {
    const time = new Date(event.eventTime);
    const date = `${time.getFullYear()}-${String(time.getMonth()+1).padStart(2,'0')}-${String(time.getDate()).padStart(2,'0')}`;
    return (filters.get('past') === '1' || time.getTime() > now)
      && (!search || `${event.name} ${event.venueName}`.toLocaleLowerCase().includes(search))
      && (!filters.get('city') || event.venueCity === filters.get('city'))
      && (!filters.get('category') || (event.category ?? 'Other') === filters.get('category'))
      && (!filters.get('from') || date >= filters.get('from')!)
      && (!filters.get('to') || date <= filters.get('to')!);
  }).sort((a,b)=>Date.parse(a.eventTime)-Date.parse(b.eventTime));
}
