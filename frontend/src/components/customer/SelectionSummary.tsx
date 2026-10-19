import { useQuery } from '@tanstack/react-query';
import api from '../../lib/api';
import type { SeatResponse } from '../../types/api';
import { useSessionStore } from '../../store/sessionStore';
import { money } from '../../lib/money';
export function SelectionSummary({ selectedSeats }: { selectedSeats: SeatResponse[] }) {
  const eventId = useSessionStore(s => s.event?.id);
  const prices = useQuery({ queryKey: ['prices', eventId], enabled: !!eventId,
    queryFn: () => api.get<{ currency: string; feeMinor: number; sections: { section: string; amountMinor: number }[] }>(`/events/${eventId}/prices`).then(r => r.data) });
  const amounts = selectedSeats.map(seat => prices.data?.sections.find(p => p.section === seat.section)?.amountMinor);
  const known = !!prices.data && amounts.every(value => value !== undefined);
  return <section className="rounded-xl bg-slate-100 dark:bg-slate-800 p-5 space-y-3 text-slate-900 dark:text-white">
    <h2 className="text-lg font-semibold">Selection Summary</h2>
    {!selectedSeats.length ? <p>Select available seats to see their prices.</p> : <>
      {selectedSeats.map((seat, index) => <p key={seat.id} className="flex justify-between"><span>{seat.seatNumber} · {seat.section}</span><span>{amounts[index] === undefined ? 'Price unavailable' : money(amounts[index]!)}</span></p>)}
      {known ? <><p>Booking fee: {money(prices.data!.feeMinor)}</p><p className="font-bold">Estimated total: {money(amounts.reduce<number>((sum, value) => sum + (value ?? 0), 0) + prices.data!.feeMinor)}</p></> : <p role="status">{prices.error ? 'Unable to load prices.' : 'Loading prices…'}</p>}
      <p className="text-sm">Review your total at checkout. Demo booking; no money will be charged.</p>
    </>}
  </section>;
}
