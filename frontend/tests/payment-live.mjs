import { chromium } from 'playwright';
import { randomUUID } from 'node:crypto';
import { execFileSync } from 'node:child_process';
import { mkdir, writeFile } from 'node:fs/promises';
import assert from 'node:assert/strict';
import { accounts, send, createAccount, disposeAccounts } from './auth-helper.mjs';
if (process.env.SEATLOCK_LIVE_TEST !== '1') throw new Error('Set SEATLOCK_LIVE_TEST=1 to create dedicated payment test data.');
const sql = query => execFileSync('docker', ['compose','exec','-T','postgres','psql','-U','admin','-d','seatlock','-v','ON_ERROR_STOP=1','-At'], { cwd: '..', input: query, encoding: 'utf8' }).trim();
const report={startedAt:new Date().toISOString(),checks:[]};
const pass=name=>{report.checks.push(name);console.log('PASS',name);};
let browser;
await mkdir('test-results',{recursive:true});
try {
  const admin=await createAccount(sql,true), customer=await createAccount(sql);
  const adminApi=accounts.get(admin), api=accounts.get(customer);
  async function adminPost(path,body) {
    const csrf=await (await adminApi.get('/api/auth/csrf')).json();
    const response=await adminApi.post(path,{data:body,headers:{[csrf.headerName]:csrf.token}});
    assert.ok(response.ok(),await response.text()); return response;
  }
  const venue=await (await adminPost('/api/admin/catalog/venues',{name:'Payment venue '+randomUUID(),city:'Mumbai',address:'Demo address',timezone:'Asia/Kolkata',sections:[{name:'Standard',prefix:'P',seats:4}]})).json();
  const event=await (await adminPost('/api/admin/catalog/events',{venueId:venue.id,name:'Payment validation '+randomUUID(),saleStartTime:new Date(Date.now()-60000).toISOString(),saleEndTime:new Date(Date.now()+3600000).toISOString(),eventTime:new Date(Date.now()+7200000).toISOString(),feeMinor:2500,prices:[{section:'Standard',amountMinor:12345}]})).json();
  await adminPost(`/api/admin/catalog/events/${event.id}/publish`,{});report.eventId=event.id;
  const seats=(await send(api,`/api/events/${event.id}/seats`)).data;
  browser=await chromium.launch({headless:true,...(process.env.BROWSER_CHANNEL?{channel:process.env.BROWSER_CHANNEL}:{})});
  const context=await browser.newContext({storageState:await api.storageState()});const page=await context.newPage();const errors=[];page.on('pageerror',e=>errors.push(e.message));
  async function checkout(seat) {
    await page.goto(`http://127.0.0.1:5173/events/${event.id}`);
    await page.locator(`[data-seat-id="${seat.id}"]`).click();
    await page.getByRole('button',{name:/Continue to checkout/}).click();
    await page.waitForURL('**/checkout');
    await page.getByRole('button',{name:'Continue to payment',exact:true}).click();
  }
  await checkout(seats[0]);
  await page.getByLabel('Demo payment outcome').selectOption('FAILURE');
  await page.getByRole('button',{name:'Confirm demo booking',exact:true}).click();
  await page.getByText(/Demo payment was declined/).waitFor();
  assert.equal(sql(`SELECT count(*) FROM booking WHERE event_id='${event.id}'`),'0');
  await page.getByRole('link',{name:'Back to checkout'}).click();
  await page.getByRole('button',{name:'Continue to payment',exact:true}).click();
  await page.getByRole('button',{name:'Confirm demo booking',exact:true}).click();
  await page.waitForURL('**/booking-confirmed/*');
  assert.equal(sql(`SELECT count(*) FROM payment_attempt p JOIN checkout_quote q ON q.id=p.quote_id WHERE q.event_id='${event.id}' AND p.status='FAILED'`),'1');
  assert.equal(sql(`SELECT count(*) FROM booking WHERE event_id='${event.id}'`),'1');
  pass('declined payment creates no booking; a fresh checkout succeeds with one booking');
  await checkout(seats[1]);
  await page.getByLabel('Demo payment outcome').selectOption('DELAYED_SUCCESS');
  await page.getByRole('button',{name:'Confirm demo booking',exact:true}).click();
  await page.getByText(/Your demo payment is being checked/).waitFor();
  await page.screenshot({path:'test-results/payment-live-pending.png',fullPage:true});
  await page.reload();
  await page.waitForURL('**/booking-confirmed/*',{timeout:45000});
  await page.reload();await page.getByRole('region',{name:'Price breakdown'}).waitFor();
  assert.match(await page.getByRole('region',{name:'Price breakdown'}).innerText(),/148\.45/);
  assert.equal(sql(`SELECT count(*) FROM booking WHERE event_id='${event.id}'`),'2');
  pass('delayed payment survives refresh and produces a priced receipt');
  assert.equal((await send(api,`/api/seats/${seats[2].id}/hold`,'POST',{})).status,200);
  const quote=(await send(api,'/api/bookings/quote','POST',{seatIds:[seats[2].id]})).data;
  const payload={quoteId:quote.id,seatIds:[seats[2].id],idempotencyKey:randomUUID(),method:'UPI',scenario:'DELAYED_SUCCESS',amountMinor:1};
  const attempt=(await send(api,'/api/payments','POST',payload)).data;
  assert.equal(attempt.amountMinor,14845);
  sql(`UPDATE payment_attempt SET expires_at=CURRENT_TIMESTAMP-interval '1 second' WHERE id='${attempt.id}'`);
  const expired=(await send(api,`/api/payments/${attempt.id}/process`,'POST',{})).data;
  assert.equal(expired.status,'EXPIRED');assert.equal(expired.booking,null);
  assert.equal((await send(api,'/api/payments','POST',payload)).data.id,attempt.id);
  assert.equal(sql(`SELECT count(*) FROM booking WHERE event_id='${event.id}'`),'2');
  pass('late outcome expires without booking; replay and authoritative amount remain safe');
  assert.deepEqual(errors,[]);report.result='PASS';
} catch(error) { report.result='FAIL';report.error=String(error);throw error; }
finally {await writeFile('test-results/payment-validation.json',JSON.stringify(report,null,2));await browser?.close();await disposeAccounts();}
