import { test, beforeEach } from 'node:test';
import assert from 'node:assert/strict';
import { AxiosError } from 'axios';
import api, { apiClient } from '../src/lib/api.ts';

beforeEach(() => { api.defaults.adapter = async config => ({
  data: [{ id: 'event-1' }], status: 200, statusText: 'OK', headers: {}, config,
}); });
test('API client returns the response body rather than AxiosResponse', async () => {
  assert.deepEqual(await apiClient.getEvents(), [{ id: 'event-1' }]);
});
test('business errors retain status and useful message', async () => {
  api.defaults.adapter = async config => { throw new AxiosError('Conflict', 'ERR_BAD_REQUEST', config, null, {
    status: 409, statusText: 'Conflict', data: { message: 'Seat is held' }, headers: {}, config,
  }); };
  await assert.rejects(apiClient.holdSeat('seat-1', 'user'), (error: Error & { status?: number }) => error.status === 409 && error.message === 'Seat is held');
});
test('server internals are not exposed to the customer', async () => {
  api.defaults.adapter = async config => { throw new AxiosError('Failure', 'ERR_BAD_RESPONSE', config, null, {
    status: 500, statusText: 'Failure', data: { message: 'Private database details' }, headers: {}, config,
  }); };
  await assert.rejects(apiClient.getEvents(), (error: Error & { status?: number }) => error.status === 500 && !error.message.includes('Private'));
});
test('network errors remain distinguishable from business rejection', async () => {
  api.defaults.adapter = async () => { throw new AxiosError('Network error'); };
  await assert.rejects(apiClient.getEvents(), (error: Error & { status?: number }) => error.status === 0 && error.message.includes('Unable to reach'));
});

test('writes attach CSRF and never transmit a supplied owner', async () => {
  const writes = [];
  api.defaults.adapter = async config => {
    if (config.url === '/auth/csrf') return { data: { headerName: 'X-CSRF-TOKEN', token: 'test-token' }, status: 200, statusText: 'OK', headers: {}, config };
    writes.push(config);
    return { data: {}, status: 200, statusText: 'OK', headers: {}, config };
  };
  await apiClient.holdSeat('seat-1', 'forged-owner');
  await apiClient.confirmBooking({ userId: 'forged-owner', seatIds: ['seat-1'], idempotencyKey: 'key' });
  for (const write of writes) {
    assert.equal(write.headers.get('X-CSRF-TOKEN'), 'test-token');
    assert.equal(JSON.parse(write.data).userId, undefined);
  }
});
