import { useState, type FormEvent } from 'react';
import { useQuery, useQueryClient } from '@tanstack/react-query';
import { Link, useParams } from 'react-router-dom';
import api from '../../lib/api';
import { AdminLayout } from '../../components/admin/AdminLayout';
import { EventDetails } from '../../components/customer/EventDetails';
import { money } from '../../lib/money';

type Draft = { id: string; name: string; venue_name: string; venue_city: string; description: string; category: string; poster_url: string;
  duration_minutes: number | null; publication: string; revision: number; sale_start_time: string; sale_end_time: string | null; event_time: string;
  fee_minor: number; prices: { section: string; amountMinor: number }[] };
const field = 'block w-full mt-1 rounded border p-2 bg-white dark:bg-slate-900';
function local(value: string) { const date = new Date(value); date.setMinutes(date.getMinutes() - date.getTimezoneOffset()); return date.toISOString().slice(0,16); }
function paise(value: FormDataEntryValue | null) {
  if (!/^\d{1,7}(\.\d{1,2})?$/.test(String(value))) throw new Error('Enter an INR amount with at most two decimal places.');
  const [whole, decimal = ''] = String(value).split('.'); return Number(whole) * 100 + Number(decimal.padEnd(2,'0'));
}
export function EventEditor() {
  const { eventId } = useParams(); const queries = useQueryClient();
  const query = useQuery({ queryKey: ['catalog-event',eventId], refetchOnWindowFocus: false, queryFn: () => api.get<Draft>(`/admin/catalog/events/${eventId}`).then(r=>r.data) });
  const [busy,setBusy] = useState(false); const [error,setError] = useState(''); const [notice,setNotice] = useState('');
  const draft = query.data;
  async function save(event: FormEvent<HTMLFormElement>) {
    event.preventDefault(); if (!draft) return;
    const form = new FormData(event.currentTarget); setBusy(true); setError(''); setNotice('');
    try {
      await api.put(`/admin/catalog/events/${eventId}`, { revision: draft.revision, name: form.get('name'), description: form.get('description'), category: form.get('category'), posterUrl: form.get('poster'),
        durationMinutes: form.get('duration') ? Number(form.get('duration')) : null,
        saleStartTime: new Date(String(form.get('start'))).toISOString(), saleEndTime: new Date(String(form.get('end'))).toISOString(), eventTime: new Date(String(form.get('time'))).toISOString(),
        feeMinor: paise(form.get('fee')), prices: draft.prices.map((price,index)=>({section:price.section,amountMinor:paise(form.get(`price-${index}`))})) });
      await query.refetch(); await queries.invalidateQueries({queryKey:['catalog-events']}); setNotice('Draft saved. Review the saved preview below before publishing.');
    } catch (error) { setError((error as Error).message); } finally { setBusy(false); }
  }
  return <AdminLayout><main className="max-w-3xl space-y-6 text-slate-900 dark:text-white">
    <Link to="/admin/catalog" className="text-indigo-700 dark:text-indigo-300">Back to events and venues</Link>
    <h1 className="text-2xl font-bold">Edit and preview event</h1>
    {query.isLoading && <p role="status">Loading event…</p>}
    {(error || query.error) && <div role="alert"><p>{error || 'Unable to load this event.'}</p><button onClick={()=>{setError(''); void query.refetch();}}>Reload saved event</button></div>}
    {notice && <p role="status">{notice}</p>}
    {draft && <>
      {draft.publication === 'DRAFT' ? <form key={`${draft.id}-${draft.revision}`} onSubmit={save} className="space-y-4 border rounded-xl p-5">
        <p>Venue and seating stay fixed. Only drafts can be edited. Save to update the preview.</p>
        <label className="block">Event name<input name="name" className={field} required maxLength={255} defaultValue={draft.name} /></label>
        <label className="block">Description<textarea name="description" className={field} rows={5} maxLength={5000} defaultValue={draft.description} /></label>
        <label className="block">Category<select aria-label="Category" name="category" className={field} defaultValue={draft.category}>{['Music','Comedy','Theatre','Sports','Workshop','Other'].map(value=><option key={value}>{value}</option>)}</select></label>
        <label className="block">Poster image URL<input name="poster" type="url" className={field} maxLength={2048} placeholder="https://…" defaultValue={draft.poster_url} /></label>
        <p className="text-sm">Optional HTTPS image hosted by you or a provider you trust. A placeholder appears if it cannot load.</p>
        <label className="block">Duration (minutes)<input name="duration" className={field} type="number" min={1} max={1440} defaultValue={draft.duration_minutes ?? ''} /></label>
        <p className="text-sm">Dates use {Intl.DateTimeFormat().resolvedOptions().timeZone}.</p>
        <label className="block">Sales start<input name="start" className={field} type="datetime-local" required defaultValue={local(draft.sale_start_time)} /></label>
        <label className="block">Sales close<input name="end" className={field} type="datetime-local" required defaultValue={local(draft.sale_end_time ?? draft.event_time)} /></label>
        <label className="block">Event time<input name="time" className={field} type="datetime-local" required defaultValue={local(draft.event_time)} /></label>
        {draft.prices.map((price,index)=><label className="block" key={price.section}>{price.section} price (INR)<input name={`price-${index}`} className={field} inputMode="decimal" required defaultValue={(price.amountMinor/100).toFixed(2)} /></label>)}
        <label className="block">Booking fee (INR)<input name="fee" className={field} inputMode="decimal" required defaultValue={(draft.fee_minor/100).toFixed(2)} /></label>
        <button disabled={busy} className="rounded bg-indigo-700 px-4 py-2 text-white">{busy?'Saving…':'Save draft'}</button>
      </form> : <p>This event is {draft.publication.toLowerCase()}. Editing is disabled to protect existing reservations and bookings.</p>}
      <section aria-label="Saved event preview" className="space-y-4 rounded-xl border p-5">
        <h2 className="text-xl font-bold">Saved preview: {draft.name}</h2><p>{draft.publication === 'DRAFT' ? 'Private preview — customers cannot see this draft.' : 'Published event preview'}</p>
        <EventDetails event={{id:draft.id,name:draft.name,venueName:draft.venue_name,venueCity:draft.venue_city,eventTime:draft.event_time,saleStartTime:draft.sale_start_time,description:draft.description,category:draft.category,posterUrl:draft.poster_url,durationMinutes:draft.duration_minutes}} />
        <p>{draft.prices.map(price=>`${price.section}: ${money(price.amountMinor)}`).join(' · ')} · Booking fee: {money(draft.fee_minor)}</p>
        <p>Return to events and venues to publish this saved draft or manage sales.</p>
      </section>
    </>}
  </main></AdminLayout>;
}
