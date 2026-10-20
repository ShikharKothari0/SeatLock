import test from 'node:test';
import assert from 'node:assert/strict';
import { sectionRows, rowLabel } from '../src/lib/seatLayout.ts';

test('sections never mix matching seat suffixes and preserve IDs with natural ordering',()=>{
  const seats=[{id:'s1',section:'Standard',seatNumber:'S1'},{id:'p10',section:'Premium',seatNumber:'P10'},{id:'p2',section:'Premium',seatNumber:'P2'},{id:'p1',section:'Premium',seatNumber:'P1'}];
  const groups=sectionRows(seats,[{section:'Premium',displayOrder:0,seatsPerRow:2,centreAisle:true,amountMinor:89900},{section:'Standard',displayOrder:1,seatsPerRow:4}]);
  assert.deepEqual(groups.map(s=>s.section),['Premium','Standard']);
  assert.deepEqual(groups[0].rows.map(row=>row.map(s=>s.id)),[['p1','p2'],['p10']]);
  assert.equal(groups[0].amountMinor,89900);assert.equal(groups[0].centreAisle,true);
  assert.equal(seats[0].id,'s1');
});
test('legacy and nonnumeric seats remain visible and rows extend beyond Z',()=>{
  const seats=Array.from({length:300},(_,i)=>({section:'Legacy',seatNumber:`X${i+1}`}));
  seats.push({section:'Legacy',seatNumber:'Accessible'});
  const groups=sectionRows(seats);
  assert.equal(groups[0].rows.flat().length,301);
  assert.equal(groups[0].rows.length,31);
  assert.equal(rowLabel(25),'Z');assert.equal(rowLabel(26),'AA');assert.equal(rowLabel(52),'BA');
});
