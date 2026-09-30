import http from 'k6/http';
import { check } from 'k6';

const baseUrl = __ENV.BASE_URL || 'http://127.0.0.1:18080';
const apiKey = __ENV.API_KEY || 'qg_sk_test123';

export const options = {
  scenarios: {
    quota_load: {
      executor: 'constant-vus',
      vus: Number(__ENV.VUS || 1000),
      duration: __ENV.DURATION || '30s',
    },
  },
  thresholds: {
    http_req_failed: ['rate<0.01'],
  },
};

export default function () {
  const response = http.post(
    `${baseUrl}/v1/chat/completions`,
    JSON.stringify({
      model: 'gpt-standard',
      messages: [{ role: 'user', content: 'quota concurrency benchmark' }],
      stream: false,
    }),
    {
      headers: {
        Authorization: `Bearer ${apiKey}`,
        'Content-Type': 'application/json',
      },
      tags: { scenario: 'quota_load' },
    },
  );

  check(response, {
    'quota request returned a response': (result) => result.status > 0,
  });
}