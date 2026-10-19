import type { PriceQuote } from '../../types/api';
import { money } from '../../lib/money';
export function PriceBreakdown({ quote }: { quote: PriceQuote }) {
  return <section className="rounded-xl bg-slate-100 dark:bg-slate-800 p-5 space-y-2 text-slate-900 dark:text-white" aria-label="Price breakdown">
    {quote.lines.map(line => <p key={line.seatId} className="flex justify-between"><span>{line.seatNumber} · {line.section}</span><span>{money(line.amountMinor, quote.currency)}</span></p>)}
    <p className="flex justify-between"><span>Subtotal</span><span>{money(quote.subtotalMinor, quote.currency)}</span></p>
    <p className="flex justify-between"><span>Booking fee</span><span>{money(quote.feeMinor, quote.currency)}</span></p>
    <p className="flex justify-between font-bold border-t pt-2"><span>Total</span><span>{money(quote.totalMinor, quote.currency)}</span></p>
    <p className="text-sm">Demo booking. No money is charged; no tax is applied in this demo.</p>
  </section>;
}
