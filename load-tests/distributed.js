import http from 'k6/http';
import { check } from 'k6';

export const options = {
    vus: 10,
    duration: '30s',

    thresholds: {
        http_req_failed: ['rate<0.05'],
        http_req_duration: ['p(95)<500'],
    },
};

const GATEWAYS = [
    'http://host.docker.internal:8081',
    'http://host.docker.internal:8082',
    'http://host.docker.internal:8083',
];

export default function () {
    const baseUrl = GATEWAYS[(__VU - 1) % GATEWAYS.length];

    const payload = JSON.stringify({
        requestId: `k6-distributed-${__VU}-${__ITER}-${Date.now()}`,
        subject: {
            subjectId: 'load-test-user',
            type: 'USER',
        },
        resource: '/api/orders',
        requestedAt: new Date().toISOString(),
    });

    const response = http.post(
        `${baseUrl}/api/v1/traffic/evaluate`,
        payload,
        {
            headers: {
                'Content-Type': 'application/json',
            },
            timeout: '5s',
        }
    );

    check(response, {
        'status is 200': (r) => r.status === 200,
        'response contains decision': (r) =>
            r.status === 200 &&
            r.body != null &&
            (r.body.includes('ALLOWED') || r.body.includes('REJECTED')),
    });
}