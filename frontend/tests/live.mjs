import { accounts, send, createAccount, disposeAccounts } from './auth-helper.mjs';
import { chromium } from 'playwright';
import { randomUUID } from 'node:crypto';
import { execFileSync, spawnSync } from 'node:child_process';
import { mkdir, writeFile } from 'node:fs/promises';
import assert from 'node:assert/strict';

if (process.env.SEATLOCK_LIVE_TEST !== '1') throw new Error('Set SEATLOCK_LIVE_TEST=1 to authorize creating dedicated live validation records.');
const api = 'http://127.0.0.1:8080';
const ui = 'http://127.0.0.1:5173';
const report = { startedAt: new Date().toISOString(), eventId: randomUUID(), venueId: randomUUID(), users: Array.from({ length: 4 }, randomUUID), seats: Array.from({ length: 12 }, randomUUID), checks: [], bookings: [], observations: [] };
const out = 'test-results/live-validation.json';
await mkdir('test-results', { recursive: true });
const save = () => Promise.all([out, `test-results/live-${report.eventId}.json`].map(path => writeFile(path, JSON.stringify(report, null, 2))));
const check = async (name, fn) => { const detail = await fn(); report.checks.push({ name, passed: true, detail }); console.log('PASS', name); await save(); return detail; };
const sql = query => execFileSync('docker', ['compose', 'exec', '-T', 'postgres', 'psql', '-U', 'admin', '-d', 'seatlock', '-v', 'ON_ERROR_STOP=1', '-At'], { cwd: '..', input: query, encoding: 'utf8', timeout: 15_000 }).trim();
const redis = (...args) => execFileSync('docker', ['compose', 'exec', '-T', 'redis', 'redis-cli', '--raw', ...args], { cwd: '..', encoding: 'utf8', timeout: 10_000 }).trim();
async function request(path, payload) {
  const owner = payload?.userId ?? admin;
  return send(accounts.get(owner), path.startsWith('/actuator/') ? api + path : path, payload ? 'POST' : 'GET', payload);
}
const hold = (seatId, userId) => request(`/api/seats/${seatId}/hold`, { userId });
const confirm = (seatIds, userId, idempotencyKey = randomUUID()) => request('/api/bookings/confirm', { seatIds, userId, idempotencyKey });
let browser, admin, customer;
try {
  admin = await createAccount(sql, true); customer = await createAccount(sql);
  report.users = []; for (let i = 0; i < 4; i++) report.users.push(await createAccount(sql));
  await check('backend and real frontend proxy respond', async () => {
    assert.equal((await request('/actuator/health')).data.status, 'UP');
    assert.equal((await fetch(ui + '/api/events')).status, 200);
  });
  await check('metrics stream survives disconnect and reconnect', async () => {
    async function connect() {
      const controller = new AbortController();
      const response = await fetch(api + '/api/admin/metrics/stream', { headers: { Cookie: (await accounts.get(admin).storageState()).cookies.map(c => c.name + '=' + c.value).join('; ') }, signal: AbortSignal.any([controller.signal, AbortSignal.timeout(20000)]) });
      assert.equal(response.status, 200);
      const reader = response.body.getReader();
      const first = await reader.read();
      assert.match(new TextDecoder().decode(first.value), /metrics/);
      return { controller, reader };
    }
    const first = await connect();
    const survivor = await connect();
    first.controller.abort();
    await new Promise(resolve => setTimeout(resolve, 7000));
    const next = await survivor.reader.read();
    assert.equal(next.done, false);
    const reconnected = await connect();
    survivor.controller.abort();
    reconnected.controller.abort();
  });
  const stamp = new Date().toISOString().replace(/[^0-9T]/g, '').slice(0, 15);
  sql(`BEGIN;
INSERT INTO venue VALUES ('${report.venueId}', 'Live Validation Arena', 'Mumbai', 12);
INSERT INTO event (id,name,venue_id,sale_start_time,event_time) VALUES ('${report.eventId}', 'Live validation ${stamp}', '${report.venueId}', NOW() - INTERVAL '1 day', NOW() + INTERVAL '7 days');
${report.seats.map((id, index) => `INSERT INTO seat (id,event_id,seat_number,section,status,version) VALUES ('${id}','${report.eventId}','A${index + 1}','Validation','AVAILABLE',0);`).join('\n')}
INSERT INTO event_price SELECT DISTINCT event_id,section,50000 FROM seat WHERE event_id='${report.eventId}';
COMMIT;`);
  await save();
  const expiryStarted = Date.now();
  await check('five-minute expiry hold created', async () => {
    assert.equal((await hold(report.seats[0], report.users[0])).status, 200);
    const ttl = Number(redis('TTL', 'seat:lock:' + report.seats[0]));
    assert.ok(ttl > 290 && ttl <= 300);
    return { seatId: report.seats[0], initialTtlSeconds: ttl };
  });
  await check('two distinct users contend: exactly one hold succeeds', async () => {
    const outcomes = await Promise.all([hold(report.seats[1], report.users[1]), hold(report.seats[1], report.users[2])]);
    assert.deepEqual(outcomes.map(result => result.status).sort(), [200, 409]);
    const winner = outcomes[0].status === 200 ? report.users[1] : report.users[2];
    const loser = winner === report.users[1] ? report.users[2] : report.users[1];
    assert.equal((await confirm([report.seats[1]], loser)).status, 409);
    const key = randomUUID();
    const first = await confirm([report.seats[1]], winner, key);
    assert.equal(first.status, 200);
    const retry = await confirm([report.seats[1]], winner, key);
    assert.equal(retry.status, 200);
    assert.equal(first.data.id, retry.data.id);
    assert.equal(redis('EXISTS', 'seat:lock:' + report.seats[1]), '0');
    report.bookings.push(first.data);
    return { statuses: outcomes.map(result => result.status), wrongOwnerStatus: 409, bookingId: first.data.id, repeatedBookingId: retry.data.id };
  });
  await check('sixth hold in a ten-second user window is throttled', async () => {
    const statuses = [];
    for (const id of report.seats.slice(2, 8)) statuses.push((await hold(id, report.users[3])).status);
    assert.deepEqual(statuses, [200, 200, 200, 200, 200, 429]);
    return statuses;
  });
  browser = await chromium.launch({ headless: true, ...(process.env.BROWSER_CHANNEL ? { channel: process.env.BROWSER_CHANNEL } : {}) });
  const context = await browser.newContext({ storageState: await accounts.get(customer).storageState(), viewport: { width: 1280, height: 900 } });
  const page = await context.newPage();
  const pageErrors = [];
  page.on('pageerror', error => pageErrors.push(error.message));
  await check('real browser multi-seat booking, receipt refresh and history', async () => {
    await page.goto(ui + '/events/' + report.eventId);
    for (const seatId of report.seats.slice(10, 12)) await page.locator(`[data-seat-id="${seatId}"]`).click();
    await page.getByRole('button', { name: /Continue to checkout/ }).click();
    await page.waitForURL('**/checkout');
    await page.reload();
    await page.getByRole('button', { name: 'Continue to payment' }).click();
    const responsePromise = page.waitForResponse(r => r.url().includes('/api/payments/') && r.url().endsWith('/process') && r.request().method() === 'POST');
    await page.getByRole('button', { name: 'Confirm demo booking' }).click();
    const response = await responsePromise;
    assert.equal(response.status(), 200);
    const booking = (await response.json()).booking;
    report.bookings.push(booking);
    await page.waitForURL('**/booking-confirmed/' + booking.id);
    await page.reload();
    await page.getByRole('heading', { name: 'Booking confirmed' }).waitFor();
    assert.match(await page.locator('main').innerText(), /A11/);
    assert.match(await page.locator('main').innerText(), /A12/);
    await page.screenshot({ path: 'test-results/live-booking-receipt.png', fullPage: true });
    await page.goto(ui + '/my-bookings');
    const savedBooking = page.locator('article').filter({ has: page.locator(`a[href="/booking-confirmed/${booking.id}"]`) });
    await savedBooking.waitFor();
    assert.match(await savedBooking.innerText(), /A11/);
    assert.match(await savedBooking.innerText(), /A12/);
    for (const seatId of report.seats.slice(10, 12)) assert.equal(redis('EXISTS', 'seat:lock:' + seatId), '0');
    assert.deepEqual(pageErrors, []);
    return { bookingId: booking.id, seatIds: booking.seatIds };
  });
  await check('browser handles a real concurrent-seat conflict without losing its first hold', async () => {
    await page.goto(ui + '/events/' + report.eventId);
    for (const seatId of report.seats.slice(8, 10)) await page.locator(`[data-seat-id="${seatId}"]`).click();
    assert.equal((await hold(report.seats[9], report.users[0])).status, 200);
    await page.getByRole('button', { name: /Continue to checkout/ }).click();
    await page.getByRole('alert').waitFor();
    await page.getByRole('button', { name: 'Continue with 1 reserved seat(s)' }).click();
    await page.waitForURL('**/checkout');
    await page.getByRole('button', { name: 'Continue to payment' }).click();
    const responsePromise = page.waitForResponse(r => r.url().includes('/api/payments/') && r.url().endsWith('/process') && r.request().method() === 'POST');
    await page.getByRole('button', { name: 'Confirm demo booking' }).click();
    const response = await responsePromise;
    assert.equal(response.status(), 200);
    const booking = (await response.json()).booking;
    assert.deepEqual(booking.seatIds, [report.seats[8]]);
    report.bookings.push(booking);
    await page.waitForURL('**/booking-confirmed/' + booking.id);
    return { bookingId: booking.id, seatIds: booking.seatIds };
  });
  await check('PostgreSQL contains the expected confirmed bookings and seats', async () => {
    const result = sql(`SELECT count(*) FROM booking WHERE event_id='${report.eventId}' AND status='CONFIRMED';
SELECT count(*) FROM seat WHERE event_id='${report.eventId}' AND status='CONFIRMED';`).split(/\r?\n/);
    assert.deepEqual(result, ['3', '4']);
    return { confirmedBookings: 3, confirmedSeats: 4 };
  });
  report.overview = (await request('/api/admin/metrics/overview')).data;
  await save();
  console.log('Waiting for the original five-minute Redis TTL and scheduled expiry reconciliation.');
  const deadline = expiryStarted + 390_000;
  while (Date.now() < deadline) {
    const state = sql(`SELECT status FROM seat WHERE id='${report.seats[0]}';`);
    if (state === 'AVAILABLE') break;
    console.log('Expiry elapsed seconds:', Math.floor((Date.now() - expiryStarted) / 1000));
    await new Promise(resolve => setTimeout(resolve, 15_000));
  }
  await check('real five-minute hold expires in Redis and PostgreSQL; confirmation is rejected', async () => {
    assert.equal(redis('EXISTS', 'seat:lock:' + report.seats[0]), '0');
    assert.equal(sql(`SELECT status FROM seat WHERE id='${report.seats[0]}';`), 'AVAILABLE');
    assert.equal((await confirm([report.seats[0]], report.users[0])).status, 409);
    const listing = (await request('/api/events/' + report.eventId + '/seats')).data;
    assert.equal(listing.find(seat => seat.id === report.seats[0]).status, 'AVAILABLE');
    return { elapsedSeconds: Math.floor((Date.now() - expiryStarted) / 1000), confirmStatus: 409 };
  });
  await check('Kafka contains hold, confirmation and release events for this run', async () => {
    const matched = {};
    for (const topic of ['seat-held', 'seat-confirmed', 'seat-released']) {
      const result = spawnSync('docker', ['compose', 'exec', '-T', 'kafka', 'kafka-console-consumer', '--bootstrap-server', 'localhost:9092', '--topic', topic, '--from-beginning', '--timeout-ms', '8000'], { cwd: '..', encoding: 'utf8', timeout: 25_000 });
      if (result.error) throw result.error;
      const records = (result.stdout ?? '').split(/\r?\n/).flatMap(line => { try { return [JSON.parse(line)]; } catch { return []; } });
      matched[topic] = records.filter(record => record.eventId === report.eventId);
      assert.ok(matched[topic].length > 0, topic + ' missing run records');
    }
    for (const booking of report.bookings) assert.ok(matched['seat-confirmed'].some(record => record.bookingId === booking.id));
    assert.ok(matched['seat-released'].some(record => record.seatId === report.seats[0]));
    return Object.fromEntries(Object.entries(matched).map(([topic, records]) => [topic, records.length]));
  });
  report.finishedAt = new Date().toISOString();
  report.result = 'PASS';
  await save();
  console.log('Live validation passed. Dedicated test event retained:', report.eventId);
} catch (error) {
  report.result = 'FAIL';
  report.error = error.stack;
  await save();
  throw error;
} finally {
  await browser?.close(); await disposeAccounts();
}
