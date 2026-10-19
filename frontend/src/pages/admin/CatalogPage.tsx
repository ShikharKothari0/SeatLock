import { Link } from 'react-router-dom';
import { useState, type FormEvent } from 'react';
import { useQuery, useQueryClient } from '@tanstack/react-query';
import api from '../../lib/api';
import { AdminLayout } from '../../components/admin/AdminLayout';
import { money } from '../../lib/money';
type Venue = { id: string; name: string; city: string; timezone: string; layout_seats: number };
type Event = { id: string; name: string; venue_name: string; publication: string; event_time: string; fee_minor: number; prices: { section: string; amountMinor: number }[] };
const field = 'block w-full border rounded p-2 bg-white dark:bg-slate-900 mt-1';
function minor(value: string) {
  if (!/^\d{1,6}(\.\d{1,2})?$/.test(value)) throw new Error('Enter an INR amount with at most two decimal places.');
  const [whole, fraction = ''] = value.split('.'); return Number(whole) * 100 + Number(fraction.padEnd(2, '0'));
}
export function CatalogPage() {
  const queries = useQueryClient();
  const [venue, setVenue] = useState('');
  const [sections, setSections] = useState([{ name: 'Standard', prefix: 'A', seats: 20 }]);
  const [busy, setBusy] = useState(false); const [notice, setNotice] = useState(''); const [error, setError] = useState('');
  const venues = useQuery({ queryKey: ['catalog-venues'], queryFn: () => api.get<Venue[]>('/admin/catalog/venues').then(r => r.data) });
  const events = useQuery({ queryKey: ['catalog-events'], queryFn: () => api.get<Event[]>('/admin/catalog/events').then(r => r.data) });
  const layout = useQuery({ queryKey: ['catalog-sections', venue], enabled: !!venue, queryFn: () => api.get<{ section: string; seats: number }[]>(`/admin/catalog/venues/${venue}/sections`).then(r => r.data) });
  async function run(action: () => Promise<void>) {
    setBusy(true); setNotice(''); setError('');
    try { await action(); await queries.invalidateQueries({ queryKey: ['catalog-events'] }); await queries.invalidateQueries({ queryKey: ['catalog-venues'] }); await queries.invalidateQueries({ queryKey: ['events'] }); }
    catch (error) { setError((error as Error).message); } finally { setBusy(false); }
  }
  function createVenue(event: FormEvent<HTMLFormElement>) {
    event.preventDefault(); const form = new FormData(event.currentTarget);
    void run(async () => {
      const result = await api.post('/admin/catalog/venues', { name: form.get('name'), city: form.get('city'), address: form.get('address'), timezone: form.get('timezone'), sections });
      setVenue(result.data.id); setNotice('Venue and reusable seating layout created.');
    });
  }
  function createEvent(event: FormEvent<HTMLFormElement>) {
    event.preventDefault(); const form = new FormData(event.currentTarget);
    void run(async () => {
      await api.post('/admin/catalog/events', { venueId: venue, name: form.get('name'),
        saleStartTime: new Date(String(form.get('start'))).toISOString(), saleEndTime: new Date(String(form.get('end'))).toISOString(), eventTime: new Date(String(form.get('time'))).toISOString(),
        feeMinor: minor(String(form.get('fee'))), prices: layout.data!.map((section, index) => ({ section: section.section, amountMinor: minor(String(form.get(`price-${index}`))) })) });
      setNotice('Draft event created. Use Edit and preview to add details, then publish when ready.');
    });
  }
  return <AdminLayout><div className="max-w-5xl space-y-8 text-slate-900 dark:text-white">
    <h1 className="text-2xl font-bold">Events and venues</h1>
    <p>Create a reusable venue layout, then an event with section prices. All amounts are INR; no tax is applied in this demo.</p>
    {error && <p role="alert" className="text-red-600 dark:text-red-400">{error}</p>}{notice && <p role="status">{notice}</p>}
    <form onSubmit={createVenue} className="space-y-4 border rounded-xl p-5">
      <h2 className="text-xl font-semibold">1. Create a venue</h2>
      <label className="block">Venue name<input className={field} name="name" required maxLength={255} /></label>
      <label className="block">City<input className={field} name="city" required maxLength={255} /></label>
      <label className="block">Address<input className={field} name="address" required maxLength={500} /></label>
      <label className="block">Venue timezone<input className={field} name="timezone" defaultValue="Asia/Kolkata" required /></label>
      {sections.map((section, index) => <fieldset key={index} className="grid sm:grid-cols-3 gap-3"><legend>Section {index + 1}</legend>
        <label>Section name<input className={field} required maxLength={50} value={section.name} onChange={e => setSections(sections.map((s,i) => i===index ? { ...s, name: e.target.value } : s))} /></label>
        <label>Seat prefix<input className={field} required pattern="[A-Za-z0-9]{1,8}" value={section.prefix} onChange={e => setSections(sections.map((s,i) => i===index ? { ...s, prefix: e.target.value } : s))} /></label>
        <label>Seat count<input className={field} required type="number" min={1} max={1000} value={section.seats} onChange={e => setSections(sections.map((s,i) => i===index ? { ...s, seats: Number(e.target.value) } : s))} /></label>
      </fieldset>)}
      <button type="button" disabled={busy || sections.length>=20} onClick={() => setSections([...sections,{ name: '', prefix: '', seats: 20 }])}>Add section</button>
      <button disabled={busy} className="block bg-indigo-700 text-white rounded px-4 py-2">Create venue</button>
    </form>
    <form onSubmit={createEvent} className="space-y-4 border rounded-xl p-5">
      <h2 className="text-xl font-semibold">2. Create a draft event</h2>
      <label className="block">Venue<select className={field} required value={venue} onChange={e => setVenue(e.target.value)}><option value="">Choose a venue</option>{venues.data?.filter(v => v.layout_seats > 0).map(v => <option key={v.id} value={v.id}>{v.name} · {v.city}</option>)}</select></label>
      <label className="block">Event name<input className={field} name="name" required maxLength={255} /></label>
      <p className="text-sm">Enter times in your browser's timezone: {Intl.DateTimeFormat().resolvedOptions().timeZone}. They are stored as UTC instants.</p>
      <label className="block">Sales start<input className={field} name="start" type="datetime-local" required /></label>
      <label className="block">Sales close<input className={field} name="end" type="datetime-local" required /></label>
      <label className="block">Event time<input className={field} name="time" type="datetime-local" required /></label>
      {layout.data?.map((section,index) => <label className="block" key={section.section}>{section.section} price (INR) · {section.seats} seats<input className={field} name={`price-${index}`} inputMode="decimal" placeholder="500.00" required /></label>)}
      <label className="block">Booking fee per booking (INR)<input className={field} name="fee" inputMode="decimal" defaultValue="0.00" required /></label>
      <button disabled={busy || !layout.data?.length} className="bg-indigo-700 text-white rounded px-4 py-2">Create draft event</button>
    </form>
    <section className="space-y-4"><h2 className="text-xl font-semibold">3. Publish or close sales</h2>
      {(events.error || venues.error || layout.error) && <p role="alert">Unable to load catalog data. Reload to retry.</p>}
      {events.data?.map(event => <article key={event.id} className="border rounded-lg p-4 flex flex-wrap gap-4 justify-between"><div><h3 className="font-semibold">{event.name}</h3><p>{event.venue_name} · {new Date(event.event_time).toLocaleString()} · {event.publication}</p><p>{event.prices.map(price => `${price.section}: ${money(price.amountMinor)}`).join(' · ')} · Booking fee: {money(event.fee_minor)}</p></div>
        <Link to={`/admin/catalog/events/${event.id}`} className="text-indigo-700 dark:text-indigo-300">{event.publication === 'DRAFT' ? 'Edit and preview' : 'Preview event'}</Link>
        {event.publication !== 'CLOSED' && <button disabled={busy} onClick={() => void run(async () => { await api.post(`/admin/catalog/events/${event.id}/${event.publication === 'DRAFT' ? 'publish' : 'close'}`); setNotice(event.publication === 'DRAFT' ? 'Event published.' : 'Sales closed; existing bookings are retained.'); })}>{event.publication === 'DRAFT' ? 'Publish event' : 'Close sales'}</button>}
      </article>)}
    </section>
  </div></AdminLayout>;
}
