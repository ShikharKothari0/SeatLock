import { request, chromium } from 'playwright';
import assert from 'node:assert/strict';
const baseURL = process.env.SEATLOCK_TEST_URL ?? 'http://localhost:8088';
const url = new URL(baseURL);
if (!['localhost', '127.0.0.1'].includes(url.hostname)) throw new Error('This smoke test is limited to local deployment validation.');
const secure = url.protocol === 'https:';
const context = await request.newContext({baseURL, ignoreHTTPSErrors: secure});
let browser;
try {
  assert.equal((await context.get('/api/events')).status(), 200);
  assert.equal((await context.get('/actuator/health')).status(), 404);
  assert.equal((await context.get('/api/admin/catalog/events')).status(), 401);
  assert.equal((await context.post('/api/auth/register', {data:{}})).status(), 403);
  const denied = await context.get('/api/events', {headers:{Origin:'https://untrusted.invalid'}});
  assert.equal(denied.status(),403);
  const allowed = await context.get('/api/events', {headers:{Origin:baseURL}});
  assert.equal(allowed.status(),200);
  // Same-origin responses do not need Access-Control-Allow-Origin.
  assert.ok([undefined, baseURL].includes(allowed.headers()['access-control-allow-origin']));
  assert.equal((await context.get('/api/auth/csrf')).status(),200);
  const cookie = (await context.storageState()).cookies.find(c=>c.name==='JSESSIONID');
  assert.ok(cookie);
  assert.equal(cookie.httpOnly,true);
  assert.equal(cookie.sameSite,'Lax');
  assert.equal(cookie.secure,secure);
  browser = await chromium.launch({headless:true,...(process.env.BROWSER_CHANNEL?{channel:process.env.BROWSER_CHANNEL}:{})});
  const page = await browser.newPage({ignoreHTTPSErrors:secure});
  const errors=[]; page.on('pageerror',e=>errors.push(e.message));
  await page.goto(baseURL+'/login?next=%2Fmy-bookings');
  await page.getByRole('button',{name:'Sign in',exact:true}).waitFor();
  await page.reload();
  await page.getByRole('button',{name:'Sign in',exact:true}).waitFor();
  assert.deepEqual(errors,[]);
  console.log(`PASS ${url.protocol} proxy, SPA deep-link reload, CSRF, CORS, admin access and session cookie flags`);
} finally { await browser?.close(); await context.dispose(); }
