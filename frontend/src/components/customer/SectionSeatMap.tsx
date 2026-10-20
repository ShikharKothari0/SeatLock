import type { ReactNode } from 'react';
import { sectionRows, rowLabel, type SectionLayout } from '../../lib/seatLayout';
import { money } from '../../lib/money';

type MapSeat = { id: string; section: string; seatNumber: string };
export function SectionSeatMap<T extends MapSeat>({ seats, layouts, renderSeat }: {
  seats: T[]; layouts: SectionLayout[]; renderSeat: (seat:T,row:string,column:number)=>ReactNode;
}) {
  return <div className="min-w-0 space-y-6">
    <div className="mx-auto max-w-md rounded-t-full border-t-4 border-indigo-400 bg-indigo-500/10 py-3 text-center text-sm font-semibold tracking-widest">STAGE</div>
    {sectionRows(seats,layouts).map(group => <section key={group.section} aria-label={`${group.section} seating`} className="rounded-xl border border-slate-300 dark:border-slate-700 p-3 sm:p-5">
      <h3 className="text-center font-semibold text-slate-900 dark:text-white">{group.section}{group.amountMinor !== undefined && <span className="text-indigo-700 dark:text-indigo-300"> · {money(group.amountMinor)}</span>}</h3>
      <div className="overflow-x-auto py-4" tabIndex={0} aria-label={`${group.section} seat map`}>
        <div className="w-max mx-auto space-y-2">
          {group.rows.map((row,index)=><div key={index} className="flex items-center gap-2">
            <span className="w-7 text-right text-xs text-slate-500">{rowLabel(index)}</span>
            <div className="flex gap-2">
              {row.map((seat,column)=><div key={seat.id} className={group.centreAisle && column===Math.ceil(group.width/2) ? 'ml-6' : ''}>{renderSeat(seat,rowLabel(index),column+1)}</div>)}
            </div>
          </div>)}
        </div>
      </div>
    </section>)}
    <p className="text-center text-xs text-slate-500">Sections are shown from front to back. Scroll within a section to see wider rows.</p>
  </div>;
}
