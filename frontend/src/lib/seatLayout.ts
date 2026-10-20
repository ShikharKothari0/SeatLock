export type SectionLayout = { section: string; displayOrder?: number; seatsPerRow?: number; centreAisle?: boolean; amountMinor?: number };
export function sectionRows<T extends { section: string; seatNumber: string }>(seats: T[], layouts: SectionLayout[] = []) {
  const names = [...new Set(seats.map(s => s.section))];
  return names.sort((a,b) => {
    const order = (name: string) => layouts.find(s => s.section === name)?.displayOrder ?? layouts.findIndex(s => s.section === name);
    return order(a)-order(b) || a.localeCompare(b);
  }).map(section => {
    const layout = layouts.find(s => s.section === section);
    const width = Math.max(1, Math.min(20, Math.floor(layout?.seatsPerRow || 10)));
    const sorted = seats.filter(s => s.section === section).sort((a,b) => a.seatNumber.localeCompare(b.seatNumber, 'en', {numeric:true}));
    const rows: T[][] = [];
    for(let index=0;index<sorted.length;index+=width) rows.push(sorted.slice(index,index+width));
    return { section, width, centreAisle: layout?.centreAisle ?? false, amountMinor: layout?.amountMinor, rows };
  });
}
export function rowLabel(index: number): string {
  let label='';
  for(let n=index+1;n>0;n=Math.floor((n-1)/26)) label=String.fromCharCode(65+(n-1)%26)+label;
  return label;
}
