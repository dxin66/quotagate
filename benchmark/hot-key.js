import http from 'k6/http';
import { check } from 'k6';

const baseUrl = __ENV.BASE_URL || 'http://127.0.0.1:18080';
const apiKey = __ENV.API_KEY || 'qg_sk_test123';

export const options = {
  scenarios: {
    hot_key: {
      executor: 'constant-vus',
      vus: Number(__ENV.VUS || 500),
      duration: __ENV.DURATION || '30s',
    },
  },
  thresholds: {
    http_req_failed: ['rate<0.01'],
    http_req_duration: ['p(95)<500'],
  },
};

export default function () {
  const response = http.post(
    `${baseUrl}/v1/chat/completions`,
    JSON.stringify({
      model: 'gpt-standard',
      messages: [{ role: 'user', content: 'hot key benchmark' }],
      stream: false,
    }),
    {
      headers: {
        Authorization: `Bearer ${apiKey}`,
        'Content-Type': 'application/json',
      },
      tags: { scenario: 'hot_key' },
    },
  );

  check(response, {
    'hot key request is successful': (result) => result.status === 200,
  });
}