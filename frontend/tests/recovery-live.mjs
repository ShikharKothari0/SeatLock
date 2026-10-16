import { chromium } from 'playwright';
import { randomUUID } from 'node:crypto';
import { execFileSync } from 'node:child_process';
import { mkdir, writeFile } from 'node:fs/promises';
import assert from 'node:assert/strict';

if (process.env.SEATLOCK_LIVE_TEST !== '1') throw new Error('Set SEATLOCK_LIVE_TEST=1 to create dedicated validation records.');
const api = 'http://127.0.0.1:8080', ui = 'http://127.0.0.1:5173';
const demo = '33333333-3333-3333-3333-333333333333';
const report = { startedAt: new Date().toISOString(), venue: randomUUID(), events: [randomUUID(), randomUUID()], seats: Array.from({ length: 5 }, randomUUID), owner: randomUUID(), checks: [] };
await mkdir('test-results', { recursive: true });
const sql = query => execFileSync('docker', ['compose', 'exec', '-T', 'postgres', 'psql', '-U', 'admin', '-d', 'seatlock', '-v', 'ON_ERROR_STOP=1', '-At'], { cwd: '..', input: query, encoding: 'utf8' }).trim();
const request = async (path, method = 'GET', body) => {
  const response = await fetch(api + path, { method, headers: { 'Content-Type': 'application/json' }, body: body ? JSON.stringify(body) : undefined, signal: AbortSignal.timeout(15000) });
  return { status: response.status, data: response.status === 204 ? null : await response.json() };
};
const hold = (seat, user) => request(`/api/seats/${seat}/hold`, 'POST', { userId: user });
const release = (seat, user) => request(`/api/seats/${seat}/hold?userId=${user}`, 'DELETE');
const confirm = (seatIds, userId, idempotencyKey = randomUUID()) => request('/api/bookings/confirm', 'POST', { seatIds, userId, idempotencyKey });
const check = async (name, fn) => { await fn(); report.checks.push(name); console.log('PASS', name); };
let browser;
try {
  sql(`BEGIN;
INSERT INTO venue VALUES ('${report.venue}', 'Recovery validation arena', 'Mumbai', 5);
${report.events.map((id, i) => `INSERT INTO event VALUES ('${id}', 'Recovery validation ${i + 1}', '${report.venue}', NOW()-INTERVAL '1 day', NOW()+INTERVAL '7 days');`).join('\n')}
INSERT INTO app_user VALUES ('${report.owner}', 'recovery-${report.owner}@example.invalid', 'Recovery owner');
${report.seats.map((id, i) => `INSERT INTO seat (id,event_id,seat_number,section,status,version) VALUES ('${id}','${report.events[i === 4 ? 1 : 0]}','R${i + 1}','Recovery','AVAILABLE',0);`).join('\n')}
COMMIT;`);
  await check('server deadline, active ownership, wrong-owner release and immediate rehold', async () => {
    const result = await hold(report.seats[0], report.owner); assert.equal(result.status, 200);
    const duration = Date.parse(result.data.expiresAt) - Date.parse(result.data.serverTime);
    assert.ok(duration > 290000 && duration <= 300000);
    const active = await request(`/api/seats/holds?userId=${report.owner}`);
    assert.ok(active.data.holds.some(item => item.seatId === report.seats[0]));
    assert.equal((await release(report.seats[0], demo)).status, 409);
    assert.equal((await release(report.seats[0], report.owner)).status, 204);
    assert.equal((await hold(report.seats[0], demo)).status, 200);
    // Allow the release event to arrive after reacquisition; it must not erase this hold.
    await new Promise(resolve => setTimeout(resolve, 4000));
    assert.equal(sql(`SELECT status FROM seat WHERE id='${report.seats[0]}';`), 'HELD');
    assert.equal((await release(report.seats[0], report.owner)).status, 409);
    assert.equal((await release(report.seats[0], demo)).status, 204);
  });
  await check('cross-event confirmation rejected without consuming either hold', async () => {
    for (const seat of [report.seats[1], report.seats[4]]) assert.equal((await hold(seat, report.owner)).status, 200);
    assert.equal((await confirm([report.seats[1], report.seats[4]], report.owner)).status, 409);
    const active = (await request(`/api/seats/holds?userId=${report.owner}`)).data.holds;
    assert.ok([report.seats[1], report.seats[4]].every(id => active.some(item => item.seatId === id)));
  });
  await check('idempotent retry matches user and payload; receipt ownership and pagination', async () => {
    const key = randomUUID();
    const first = await confirm([report.seats[1]], report.owner, key); assert.equal(first.status, 200);
    assert.equal((await confirm([report.seats[1]], report.owner, key)).data.id, first.data.id);
    assert.equal((await confirm([report.seats[1]], demo, key)).status, 409);
    assert.equal((await confirm([report.seats[4]], report.owner, key)).status, 409);
    assert.equal((await request(`/api/bookings/${first.data.id}?userId=${demo}`)).status, 404);
    assert.equal((await request(`/api/bookings/${first.data.id}?userId=${report.owner}`)).data.booking.id, first.data.id);
    const history = (await request(`/api/bookings?userId=${report.owner}&page=0&size=1`)).data;
    assert.equal(history.totalElements, 1); assert.equal(history.items[0].booking.id, first.data.id);
    assert.equal((await request(`/api/bookings?userId=${report.owner}&page=1&size=1`)).data.items.length, 0);
    await release(report.seats[4], report.owner);
  });
  browser = await chromium.launch({ headless: true, ...(process.env.BROWSER_CHANNEL ? { channel: process.env.BROWSER_CHANNEL } : {}) });
  await check('empty browser storage recovers a hold, releases it, and books again', async () => {
    assert.equal((await hold(report.seats[2], demo)).status, 200);
    const context = await browser.newContext(); const page = await context.newPage();
    await page.goto(ui + '/checkout');
    await page.getByRole('heading', { name: 'Checkout' }).waitFor();
    assert.match(await page.locator('main').innerText(), /R3/);
    await page.getByRole('button', { name: 'Release held seats' }).click();
    await page.waitForURL('**/events/' + report.events[0]);
    assert.equal(sql(`SELECT status FROM seat WHERE id='${report.seats[2]}';`), 'AVAILABLE');
    await page.locator(`[data-seat-id="${report.seats[2]}"]`).click();
    await page.getByRole('button', { name: /Hold selected seats/ }).click();
    await page.waitForURL('**/checkout');
    await page.getByRole('button', { name: 'Proceed to Payment' }).click();
    const response = page.waitForResponse(r => r.url().endsWith('/api/bookings/confirm') && r.request().method() === 'POST');
    await page.getByRole('button', { name: 'Confirm demo booking' }).click();
    const result = await response; assert.equal(result.status(), 200); report.bookingId = (await result.json()).id;
    await page.waitForURL('**/booking-confirmed/' + report.bookingId);
    await context.close();
  });
  await check('new browser context retrieves receipt and history from server', async () => {
    const context = await browser.newContext(); const page = await context.newPage();
    await page.goto(ui + '/booking-confirmed/' + report.bookingId);
    await page.getByRole('heading', { name: 'Booking confirmed' }).waitFor();
    assert.match(await page.locator('main').innerText(), /R3/);
    await page.screenshot({ path: 'test-results/server-receipt.png', fullPage: true });
    await page.goto(ui + '/my-bookings');
    await page.locator(`a[href="/booking-confirmed/${report.bookingId}"]`).waitFor();
    await context.close();
  });
  await check('expired hold is omitted even before scheduled database cleanup', async () => {
    assert.equal((await hold(report.seats[3], report.owner)).status, 200);
    sql(`UPDATE seat SET hold_expires_at=NOW()-INTERVAL '1 second' WHERE id='${report.seats[3]}';`);
    const active = (await request(`/api/seats/holds?userId=${report.owner}`)).data.holds;
    assert.ok(!active.some(item => item.seatId === report.seats[3]));
    assert.equal((await confirm([report.seats[3]], report.owner)).status, 409);
    await release(report.seats[3], report.owner);
  });
  report.result = 'PASS';
} catch (error) { report.result = 'FAIL'; report.error = error.stack; throw error; }
finally {
  report.finishedAt = new Date().toISOString();
  await writeFile('test-results/recovery-validation.json', JSON.stringify(report, null, 2));
  await browser?.close();
}
