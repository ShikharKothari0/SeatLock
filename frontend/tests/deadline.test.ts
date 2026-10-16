import test from 'node:test';
import assert from 'node:assert/strict';
import { localDeadline } from '../src/lib/holdDeadline.ts';

test('server duration works when client clock differs and does not add response latency', () => {
  assert.equal(localDeadline({ serverTime: '2026-10-16T00:00:00Z', expiresAt: '2026-10-16T00:02:00Z' }, 1000), 121000);
});
test('expired deadlines do not become a new five-minute hold', () => {
  assert.equal(localDeadline({ serverTime: '2026-10-16T00:02:00Z', expiresAt: '2026-10-16T00:00:00Z' }, 1000), 1000);
});
test('invalid server deadlines cannot silently create a hold', () => {
  assert.throws(() => localDeadline({ serverTime: 'invalid', expiresAt: 'invalid' }, 1000));
});
