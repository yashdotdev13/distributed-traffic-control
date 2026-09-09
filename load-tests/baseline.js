import http from 'k6/http';
import {check} from 'k6';

export const options = {
    vus: 10, duration: '30s',

    thresholds: {
        http_req_failed: ['rate<0.05'], http_req_duration: ['p(95)<500'],
    },
};

const BASE_URL = __ENV.BASE_URL || 'http://host.docker.internal:8081';

export default function () {
    const payload = JSON.stringify({
        requestId: `k6-${__VU}-${__ITER}-${Date.now()}`, subject: {
            subjectId: 'load-test-user', type: 'USER',
        }, resource: '/api/orders', requestedAt: new Date().toISOString(),
    });

    const response = http.post(`${BASE_URL}/api/v1/traffic/evaluate`, payload, {
        headers: {
            'Content-Type': 'application/json',
        }, timeout: '5s',
    });

    check(response, {
        'status is 200': (r) => r.status === 200,
        'response contains decision': (r) => r.status === 200 && r.body != null && (r.body.includes('ALLOWED') || r.body.includes('REJECTED')),
    });
}