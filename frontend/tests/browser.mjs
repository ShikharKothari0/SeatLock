import { chromium } from 'playwright';
import { spawn } from 'node:child_process';
import { mkdir } from 'node:fs/promises';
import assert from 'node:assert/strict';

const port = 4179;
const base = `http://127.0.0.1:${port}`;
const server = spawn(process.execPath, ['node_modules/vite/bin/vite.js', '--host', '127.0.0.1', '--port', String(port), '--strictPort'], { stdio: 'pipe', windowsHide: true });
let serverOutput = '';
server.stdout.on('data', data => { serverOutput += data; });
server.stderr.on('data', data => { serverOutput += data; });
let browser;
const failures = [];
const event = { id: 'event-1', name: 'Test Concert', venueName: 'Test Arena', venueCity: 'Mumbai', saleStartTime: '2026-01-01T00:00:00Z', eventTime: '2026-12-01T00:00:00Z' };
const overview = { totalBookings: 2, bookingsPerMinute: 2, totalHolds: 3, holdsExpired: 0, holdsRejected: 1, holdSuccessRate: 66.7, avgHoldLatencyMs: 12, p99HoldLatencyMs: 20 };
const circuitBreakers = [{ name: 'redisLock', state: 'CLOSED', failureRate: -1, failedCalls: 0, successfulCalls: 2, bufferedCalls: 2 }];
async function fixture(options = {}) {
  const context = await browser.newContext({ viewport: { width: 1280, height: 900 } });
  const page = await context.newPage();
  const pageErrors = [];
  page.on('pageerror', error => pageErrors.push(error.message));
  const seats = Array.from({ length: 100 }, (_, index) => ({ id: `seat-${index + 1}`, seatNumber: `A${index + 1}`, section: 'Section A', status: 'AVAILABLE' }));
  const holds = [], confirmations = [];
  const active = new Map();
  let receipt;
  const payments = new Map();
  let delayedReady = false;
  let recoveryUnavailable = !!options.recoveryUnavailable;
  let rejectConfirmation = !!options.rejectConfirmation;
  await page.route('**/api/**', async route => {
    const request = route.request();
    const url = new URL(request.url());
    let data;
    let status = 200;
    if (url.pathname === '/api/auth/me') data = { id: '33333333-3333-3333-3333-333333333333', email: 'test@example.test', displayName: 'Test', role: 'ADMIN' };
    else if (url.pathname === '/api/auth/csrf') data = { headerName: 'X-CSRF-TOKEN', token: 'fixture-token' };
    else if (url.pathname === '/api/events') data = options.catalog ?? [event];
    else if (url.pathname === '/api/events/event-1') data = event;
    else if (url.pathname === '/api/events/event-1/seats') data = seats;
    else if (url.pathname === '/api/events/event-1/prices') data = { currency: 'INR', feeMinor: 0, sections: [{ section: 'Section A', amountMinor: 50000 }] };
    else if (url.pathname === '/api/payments') {
      const payload = request.postDataJSON();
      if (!payments.has(payload.idempotencyKey)) payments.set(payload.idempotencyKey, {
        id: payload.idempotencyKey, status: 'PENDING', booking: null, ...payload,
      });
      data = payments.get(payload.idempotencyKey);
    }
    else if (url.pathname === '/api/bookings/quote') {
      const ids = request.postDataJSON().seatIds;
      data = { id: 'quote-1', userId: '33333333-3333-3333-3333-333333333333', eventId: event.id, currency: 'INR', expiresAt: new Date(Date.now()+120000).toISOString(), subtotalMinor: ids.length*50000, feeMinor: 0, totalMinor: ids.length*50000, lines: ids.map(id => ({ seatId: id, seatNumber: seats.find(s => s.id===id).seatNumber, section: 'Section A', amountMinor: 50000 })) };
    }
    else if (url.pathname === '/api/seats/holds') {
      if (recoveryUnavailable) { status = 503; data = { message: 'Unavailable' }; }
      else data = { holds: [...active.values()], serverTime: new Date().toISOString() };
    }
    else if (url.pathname === '/api/bookings') data = { items: receipt ? [receipt] : [], page: 0, size: 10, totalElements: receipt ? 1 : 0, totalPages: receipt ? 1 : 0 };
    else if (url.pathname.startsWith('/api/bookings/') && request.method() === 'GET') {
      if (receipt && url.pathname.endsWith('/' + receipt.booking.id)) data = receipt;
      else { status = 404; data = { message: 'Booking not found' }; }
    }
    else if (url.pathname.endsWith('/hold') && request.method() === 'DELETE') {
      const seatId = url.pathname.split('/')[3]; active.delete(seatId);
      seats.find(seat => seat.id === seatId).status = 'AVAILABLE'; data = {};
    }
    else if (url.pathname.endsWith('/hold')) {
      const seatId = url.pathname.split('/')[3];
      holds.push(seatId);
      if (seatId === options.failSeat) {
        status = options.holdStatus ?? 409;
        data = { message: options.failureMessage ?? (status === 500 ? 'Private database details' : 'Seat unavailable'), timestamp: new Date().toISOString() };
      } else {
        seats.find(seat => seat.id === seatId).status = 'HELD';
        data = { seatId, status: 'HELD', event, seat: seats.find(seat => seat.id === seatId), serverTime: new Date().toISOString(), expiresAt: new Date(Date.now() + 300000).toISOString() };
        active.set(seatId, data);
        if (options.loseHoldResponse) { await route.abort('connectionfailed'); return; }
      }
    } else if (url.pathname.startsWith('/api/payments/') && url.pathname.endsWith('/process')) {
      const payment = payments.get(url.pathname.split('/')[3]);
      const payload = { seatIds: payment.seatIds, quoteId: payment.quoteId, idempotencyKey: payment.idempotencyKey };
      if (payment.scenario === 'FAILURE') { payment.status = 'FAILED'; payment.resultCode = 'SIMULATED_DECLINE'; await route.fulfill({ status: 200, contentType: 'application/json', body: JSON.stringify(payment) }); return; }
      if (payment.scenario === 'DELAYED_SUCCESS' && !delayedReady) { await route.fulfill({ status: 200, contentType: 'application/json', body: JSON.stringify(payment) }); return; }
      confirmations.push(payload);
      if (rejectConfirmation) { await route.abort('connectionfailed'); return; }
      payload.seatIds.forEach(id => { seats.find(seat => seat.id === id).status = 'CONFIRMED'; });
      data = { ...payload, id: 'booking-1', eventId: event.id, status: 'CONFIRMED', createdAt: '2026-10-16T00:00:00Z' };
      payload.seatIds.forEach(id => active.delete(id));
      receipt = { booking: data, event, seats: seats.filter(seat => payload.seatIds.includes(seat.id)) };
      payment.status = 'SUCCEEDED'; payment.booking = data; data = payment;
    } else if (url.pathname === '/api/admin/metrics/overview') data = overview;
    else if (url.pathname === '/api/admin/metrics/circuit-breakers') data = circuitBreakers;
    else if (url.pathname === '/api/admin/metrics/cache') data = { cacheHits: 12, cacheMisses: 2, hitRatio: 85.7, cacheInvalidations: 0, activeCacheKeys: 1 };
    else if (url.pathname === '/api/admin/metrics/redis') data = { connected: true, activeLocks: 1, memoryUsedBytes: 1024, totalKeys: 4, hitRatio: 90, connectedClients: 2 };
    else if (url.pathname === '/api/admin/health') data = { status: 'UP', cpuUsage: 10, heapUsedBytes: 1024, heapMaxBytes: 4096, liveThreads: 5, hikariActiveConnections: 0, hikariPendingConnections: 0, circuitBreakers };
    else { status = 404; data = { message: 'Not found' }; }
    await route.fulfill({ status, contentType: 'application/json', body: JSON.stringify(data) });
  });
  return { context, page, holds, confirmations, finishDelay: () => { delayedReady = true; }, allowRecovery: () => { recoveryUnavailable = false; }, expire: () => { active.clear(); seats.forEach(seat => { if (seat.status === "HELD") seat.status = "AVAILABLE"; }); }, allowConfirmation: () => { rejectConfirmation = false; }, pageErrors };
}
async function run(name, execute) {
  try { await execute(); console.log('PASS', name); }
  catch (error) { failures.push(name); console.error('FAIL', name, error); }
}
async function select(page, ids) {
  await page.goto(base + '/events/event-1');
  for (const id of ids) await page.locator(`[data-seat-id="${id}"]`).click();
  await page.getByRole('button', { name: /Continue to checkout/ }).click();
}
try {
  for (let i = 0; i < 100; i++) {
    if (server.exitCode !== null) throw new Error(serverOutput);
    try { if ((await fetch(base)).ok) break; } catch {}
    await new Promise(resolve => setTimeout(resolve, 100));
    if (i === 99) throw new Error('Vite did not start: ' + serverOutput);
  }
  browser = await chromium.launch({ headless: true, ...(process.env.BROWSER_CHANNEL ? { channel: process.env.BROWSER_CHANNEL } : {}) });
  await mkdir('test-results', { recursive: true });
  await run('multi-seat booking, real receipt, refresh, history, unknown ID', async () => {
    const f = await fixture();
    try {
      await select(f.page, ['seat-1', 'seat-2']);
      await f.page.waitForURL('**/checkout');
      await f.page.getByRole('region', { name: 'Price breakdown' }).waitFor();
      assert.match(await f.page.locator('main').innerText(), /A1/);
      assert.match(await f.page.locator('main').innerText(), /A2/);
      await f.page.reload();
      await f.page.getByRole('button', { name: 'Continue to payment' }).click();
      await f.page.getByRole('button', { name: 'Confirm demo booking' }).click();
      await f.page.waitForURL('**/booking-confirmed/booking-1');
      assert.match(await f.page.locator('main').innerText(), /A1, A2/);
      await f.page.reload();
      await f.page.getByRole('heading', { name: 'Booking confirmed' }).waitFor();
      await f.page.evaluate(() => sessionStorage.clear());
      await f.page.goto(base + '/my-bookings');
      await f.page.getByText('2 seat(s): A1, A2', { exact: true }).waitFor();
      assert.deepEqual(f.confirmations[0].seatIds, ['seat-1', 'seat-2']);
      await f.page.goto(base + '/booking-confirmed/nonexistent');
      await f.page.getByRole('heading', { name: 'Booking details unavailable' }).waitFor();
      assert.equal(await f.page.getByRole('heading', { name: 'Booking confirmed', exact: true }).count(), 0);
      assert.deepEqual(f.pageErrors, []);
    } finally { await f.context.close(); }
  });
  await run('declined demo payment returns to checkout without a booking', async () => {
    const f = await fixture();
    try {
      await select(f.page, ['seat-1']);
      await f.page.waitForURL('**/checkout');
      await f.page.getByRole('button', { name: 'Continue to payment' }).click();
      await f.page.getByLabel('Demo payment outcome').selectOption('FAILURE');
      await f.page.getByRole('button', { name: 'Confirm demo booking' }).click();
      await f.page.getByText(/Demo payment was declined/).waitFor();
      assert.equal(f.confirmations.length, 0);
      await f.page.getByRole('link', { name: 'Back to checkout' }).click();
      await f.page.getByRole('region', { name: 'Price breakdown' }).waitFor();
    } finally { await f.context.close(); }
  });
  await run('delayed demo payment recovers after refresh with the original attempt', async () => {
    const f = await fixture();
    try {
      await select(f.page, ['seat-1']);
      await f.page.waitForURL('**/checkout');
      await f.page.getByRole('button', { name: 'Continue to payment' }).click();
      await f.page.getByLabel('Demo payment outcome').selectOption('DELAYED_SUCCESS');
      await f.page.getByRole('button', { name: 'Confirm demo booking' }).click();
      await f.page.getByText(/Your demo payment is being checked/).waitFor();
      await f.page.screenshot({ path: 'test-results/payment-pending.png', fullPage: true });
      await f.page.reload();
      await f.page.getByText(/Your demo payment is being checked/).waitFor();
      f.finishDelay();
      await f.page.waitForURL('**/booking-confirmed/booking-1');
      assert.equal(f.confirmations.length, 1);
    } finally { await f.context.close(); }
  });
  await run('partial hold conflict retains successful seats', async () => {
    const f = await fixture({ failSeat: 'seat-2' });
    try {
      await select(f.page, ['seat-1', 'seat-2']);
      await f.page.getByRole('alert').waitFor();
      await f.page.getByRole('button', { name: 'Continue with 1 reserved seat(s)' }).click();
      await f.page.waitForURL('**/checkout');
      await f.page.getByRole('region', { name: 'Price breakdown' }).waitFor();
      assert.match(await f.page.locator('main').innerText(), /A1/);
      assert.deepEqual(f.holds, ['seat-1', 'seat-2']);
      assert.deepEqual(f.pageErrors, []);
    } finally { await f.context.close(); }
  });
  await run('closed sales are explained without claiming another customer booked the seat', async () => {
    const f = await fixture({ failSeat: 'seat-1', failureMessage: 'Event sales are not open' });
    try {
      await select(f.page, ['seat-1']);
      await f.page.getByText('Ticket sales are currently closed for this event.', { exact: true }).waitFor();
      assert.equal(f.confirmations.length, 0);
    } finally { await f.context.close(); }
  });
  await run('reservation countdown expires with a clear return to seat selection', async () => {
    const f = await fixture();
    try {
      await f.page.clock.install();
      await select(f.page, ['seat-1']);
      await f.page.waitForURL('**/checkout');
      await f.page.getByRole('region', { name: 'Price breakdown' }).waitFor();
      await f.page.getByText(/Complete your booking within/).waitFor();
      await f.page.screenshot({ path: 'test-results/customer-checkout.png', fullPage: true });
      await f.page.clock.fastForward(301000);
      await f.page.getByRole('dialog', { name: 'Your reservation expired' }).waitFor();
      await f.page.getByRole('button', { name: 'Back to seat selection', exact: true }).click();
      await f.page.waitForURL('**/events/event-1');
      assert.equal(f.confirmations.length, 0);
    } finally { await f.context.close(); }
  });
  await run('rate limit and server error are not automatically retried', async () => {
    for (const holdStatus of [429, 500]) {
      const f = await fixture({ failSeat: 'seat-1', holdStatus });
      try {
        await select(f.page, ['seat-1']);
        const message = await f.page.getByRole('alert').innerText();
        assert.ok(message.includes(holdStatus === 429 ? 'wait 10 seconds' : 'We couldn’t complete your selection'));
        assert.ok(!message.includes('Private database'));
        assert.deepEqual(f.holds, ['seat-1']);
      } finally { await f.context.close(); }
    }
  });
  await run('lost confirm response retries original payload after refresh and expiry', async () => {
    const f = await fixture({ rejectConfirmation: true });
    try {
      await select(f.page, ['seat-1']);
      await f.page.waitForURL('**/checkout');
      await f.page.getByRole('button', { name: 'Continue to payment' }).click();
      await f.page.getByRole('button', { name: 'Confirm demo booking' }).click();
      await f.page.getByRole('alert').waitFor();
      assert.equal(f.confirmations.length, 3);
      const first = f.confirmations[0];
      await f.page.evaluate(() => {
        const saved = JSON.parse(sessionStorage.getItem('seatlock-session-v1'));
        saved.state.holdExpiresAt = Date.now() - 1000;
        sessionStorage.setItem('seatlock-session-v1', JSON.stringify(saved));
      });
      await f.page.reload();
      f.allowConfirmation();
      await f.page.getByRole('button', { name: 'Check booking status' }).click();
      await f.page.waitForURL('**/booking-confirmed/booking-1');
      for (const request of f.confirmations) assert.deepEqual(request, first);
      assert.deepEqual(f.pageErrors, []);
    } finally { await f.context.close(); }
  });
  await run('expired hold on reload is reconciled from server', async () => {
    const f = await fixture();
    try {
      await select(f.page, ['seat-1']);
      await f.page.waitForURL('**/checkout');
      await f.page.evaluate(() => {
        const saved = JSON.parse(sessionStorage.getItem('seatlock-session-v1'));
        saved.state.holdExpiresAt = Date.now() - 1;
        sessionStorage.setItem('seatlock-session-v1', JSON.stringify(saved));
      });
      f.expire();
      await f.page.reload();
      await f.page.waitForURL('**/events/event-1');
      assert.equal(f.confirmations.length, 0);
      assert.deepEqual(f.pageErrors, []);
    } finally { await f.context.close(); }
  });
  await run('lost hold response and empty storage recover from server; release clears hold', async () => {
    const f = await fixture({ loseHoldResponse: true });
    try {
      await select(f.page, ['seat-1']);
      await f.page.getByRole('button', { name: 'Continue with 1 reserved seat(s)' }).waitFor();
      await f.page.evaluate(() => sessionStorage.clear());
      await f.page.goto(base + '/checkout');
      await f.page.getByRole('heading', { name: 'Checkout' }).waitFor();
      await f.page.getByRole('button', { name: 'Cancel reservation' }).click();
      await f.page.waitForURL('**/events/event-1');
      assert.equal(await f.page.getByRole('button', { name: 'Continue with 1 reserved seat(s)' }).count(), 0);
    } finally { await f.context.close(); }
  });
  await run('unavailable recovery blocks reservation actions until retry succeeds', async () => {
    const f = await fixture({ recoveryUnavailable: true });
    try {
      await f.page.goto(base + '/events/event-1');
      await f.page.getByRole('button', { name: 'Check reservation again' }).waitFor();
      assert.equal(await f.page.locator('[data-seat-id]').count(), 0);
      f.allowRecovery();
      await f.page.getByRole('button', { name: 'Check reservation again' }).click();
      await f.page.locator('[data-seat-id="seat-1"]').waitFor();
      assert.deepEqual(f.pageErrors, []);
    } finally { await f.context.close(); }
  });
  await run('event discovery combines filters, survives refresh and distinguishes past events', async () => {
    const f = await fixture({ catalog: [
      { ...event, category: 'Music', durationMinutes: 90 },
      { ...event, id: 'old', name: 'Past comedy', venueCity: 'Delhi', category: 'Comedy', eventTime: '2020-01-01T00:00:00Z' },
      { ...event, id: 'closed', name: 'Closed concert', category: 'Music', publication: 'CLOSED' },
    ] });
    try {
      await f.page.goto(base + '/');
      await f.page.getByText('2 event(s) found', { exact: true }).waitFor();
      await f.page.getByLabel('Search events', { exact: true }).fill('Test Concert');
      await f.page.getByLabel('City', { exact: true }).selectOption('Mumbai');
      await f.page.getByLabel('Category', { exact: true }).selectOption('Music');
      await f.page.getByLabel('From date').fill('2026-12-01');
      await f.page.getByLabel('To date').fill('2026-12-01');
      await f.page.getByText('1 event(s) found', { exact: true }).waitFor();
      await f.page.reload();
      await f.page.getByText('1 event(s) found', { exact: true }).waitFor();
      assert.equal(await f.page.getByLabel('Search events', { exact: true }).inputValue(), 'Test Concert');
      await f.page.setViewportSize({ width: 390, height: 844 });
      await f.page.screenshot({ path: 'test-results/discovery-mobile.png', fullPage: true });
      assert.ok(await f.page.evaluate(() => document.documentElement.scrollWidth <= innerWidth + 1));
      await f.page.getByRole('button', { name: 'Clear filters' }).click();
      await f.page.waitForURL(base + '/');
      // URL-backed filters commit through a router transition, after the click.
      await f.page.getByLabel('Include past events').click();
      await f.page.waitForURL(url => url.searchParams.get('past') === '1');
      assert.equal(await f.page.getByLabel('Include past events').isChecked(), true);
      await f.page.getByText('3 event(s) found', { exact: true }).waitFor();
      await f.page.getByText('Past event — booking is closed.', { exact: true }).waitFor();
      await f.page.getByText('Ticket sales are closed.', { exact: true }).waitFor();
      await f.page.getByLabel('Search events', { exact: true }).fill('No matching event');
      await f.page.getByText(/No events match your filters/).waitFor();
      assert.deepEqual(f.pageErrors, []);
    } finally { await f.context.close(); }
  });
  await run('theme, mobile seat grid, admin pages and route guards', async () => {
    const f = await fixture();
    try {
      await f.page.goto(base + '/payment');
      await f.page.waitForURL(base + '/');
      await f.page.goto(base + '/events/event-1');
      await f.page.locator('[data-seat-id="seat-1"]').waitFor();
      await f.page.screenshot({ path: 'test-results/seats-dark.png', fullPage: true });
      const dark = await f.page.locator('#root > div').evaluate(el => getComputedStyle(el).backgroundColor);
      await f.page.getByRole('button', { name: 'Switch to light mode' }).click();
      const light = await f.page.locator('#root > div').evaluate(el => getComputedStyle(el).backgroundColor);
      assert.notEqual(dark, light);
      await f.page.screenshot({ path: 'test-results/seats-light.png', fullPage: true });
      await f.page.setViewportSize({ width: 390, height: 844 });
      await f.page.screenshot({ path: 'test-results/seats-mobile.png', fullPage: true });
      assert.ok(await f.page.evaluate(() => document.documentElement.scrollWidth <= window.innerWidth + 1));
      for (const route of ['/admin', '/admin/redis', '/admin/cache', '/admin/circuit-breakers', '/admin/kafka', '/admin/health']) {
        await f.page.goto(base + route);
        await f.page.getByRole('heading').first().waitFor();
        await f.page.getByText(/Loading (dashboard|Redis|cache|circuit|system)/).waitFor({ state: 'hidden' });
        assert.ok(await f.page.evaluate(() => document.documentElement.scrollWidth <= window.innerWidth + 1), route + ' overflows');
      }
      await f.page.screenshot({ path: 'test-results/admin-mobile.png', fullPage: true });
      await f.page.getByText('Health checks pending').waitFor();
      assert.deepEqual(f.pageErrors, []);
    } finally { await f.context.close(); }
  });
} finally {
  await browser?.close();
  server.kill();
}
if (failures.length) throw new Error(`${failures.length} browser scenario(s) failed: ${failures.join(', ')}`);
console.log('All browser checks passed using controlled API fixtures, not live infrastructure.');
