import { request } from 'playwright';
import { randomUUID } from 'node:crypto';
import assert from 'node:assert/strict';

export const accounts = new Map();
const quotes = new Map();
export async function send(context, path, method = 'GET', body) {
  if (path === '/api/bookings/confirm' && body && !body.quoteId) {
    let quoteId = quotes.get(body.idempotencyKey);
    if (!quoteId) {
      const quote = await send(context, '/api/bookings/quote', 'POST', { seatIds: body.seatIds });
      if (quote.status !== 200) return quote;
      quoteId = quote.data.id; quotes.set(body.idempotencyKey, quoteId);
    }
    body = { ...body, quoteId };
  }
  const headers = {};
  if (method !== 'GET') {
    const csrf = await (await context.get('/api/auth/csrf')).json();
    headers[csrf.headerName] = csrf.token;
  }
  const response = await context.fetch(path, { method, headers, ...(body ? { data: body } : {}) });
  return { status: response.status(), data: response.status() === 204 || response.status() === 201 ? null : await response.json() };
}
export async function createAccount(sql, admin = false) {
  const context = await request.newContext({ baseURL: process.env.SEATLOCK_TEST_URL ?? 'http://127.0.0.1:5173' });
  const email = `auth-${randomUUID()}@example.invalid`, password = randomUUID() + 'A!';
  assert.equal((await send(context, '/api/auth/register', 'POST', { email, password, displayName: 'Validation account' })).status, 201);
  async function login() {
    const csrf = await (await context.get('/api/auth/csrf')).json();
    assert.equal((await context.post('/api/auth/login', { form: { email, password }, headers: { [csrf.headerName]: csrf.token } })).status(), 204);
  }
  await login();
  const user = await (await context.get('/api/auth/me')).json();
  if (admin) { sql(`UPDATE app_user SET role='ADMIN' WHERE id='${user.id}';`); await login(); }
  accounts.set(user.id, context);
  return user.id;
}
export async function disposeAccounts() { await Promise.all([...accounts.values()].map(context => context.dispose())); }
