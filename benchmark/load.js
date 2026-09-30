import http from 'k6/http';
import { check } from 'k6';

const baseUrl = __ENV.BASE_URL || 'http://127.0.0.1:18080';
const apiKey = __ENV.API_KEY || 'qg_sk_test123';
const targetVUs = Number(__ENV.VUS || 50);

export const options = {
  scenarios: {
    normal_load: {
      executor: 'ramping-vus',
      startVUs: 0,
      stages: [
        { duration: '15s', target: targetVUs },
        { duration: '30s', target: targetVUs },
        { duration: '15s', target: 0 },
      ],
      gracefulRampDown: '5s',
    },
  },
  thresholds: {
    http_req_failed: ['rate<0.01'],
    http_req_duration: ['p(95)<500', 'p(99)<1000'],
  },
};

export default function () {
  const response = http.post(
    `${baseUrl}/v1/chat/completions`,
    JSON.stringify({
      model: 'gpt-standard',
      messages: [{ role: 'user', content: 'benchmark request' }],
      stream: false,
    }),
    {
      headers: {
        Authorization: `Bearer ${apiKey}`,
        'Content-Type': 'application/json',
      },
      tags: { scenario: 'normal_load' },
    },
  );

  check(response, {
    'chat completion is successful': (result) => result.status === 200,
    'response has request id': (result) => result.headers['X-Request-Id'] !== undefined,
  });
}