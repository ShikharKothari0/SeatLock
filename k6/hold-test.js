import http from 'k6/http';
import { check } from 'k6';
import { Trend, Counter } from 'k6/metrics';

const holdLatency = new Trend('hold_latency_ms', true);
const successCount = new Counter('hold_success');
const failCount = new Counter('hold_fail')

export const options = {
    scenarios: {
        hold_test: {
            executor: 'shared-iterations',
            vus: 50,
            iterations: 50,
            maxDuration: '30s',
        },
    },
    thresholds: {
            'hold_latency_ms': ['p(99)<2000'],   // before tuning, expect this to fail
            'http_req_duration': ['p(50)<500'],
        },
};

const BASE_URL = 'http://localhost:8080';
const USER_ID = '33333333-3333-3333-3333-333333333333';

export default function () {

    // __VU goes from 0 to 49
    const seatNumber = __VU.toString().padStart(8, '0');

    const seatId = `${seatNumber}-0000-0000-0000-000000000000`;

    const payload = JSON.stringify({
        userId: USER_ID,
    });

    const params = {
        headers: {
            'Content-Type': 'application/json',
        },
    };

    const start = Date.now();

    const res = http.post(
        `${BASE_URL}/api/seats/${seatId}/hold`,
        payload,
        params
    );
    const duration = Date.now() - start;

    holdLatency.add(duration);

    check(res, {
        'status is 200': (r) => r.status === 200,
        'not rate limited (429)' : (r) => r.status !== 429,
        'no server error (5xx)': (r) => r.status < 500,
    });

    if(res.status !== 200) {
    console.log(`VU ${__VU} — seat=${seatId} status=${res.status} body=${res.body}`);
    }
}