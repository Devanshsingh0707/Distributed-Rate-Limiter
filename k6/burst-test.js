import http from 'k6/http';
import { sleep, check } from 'k6';

export const options = {
  scenarios: {
    burst_requests: {
      executor: 'constant-arrival-rate',
      rate: 100, // 100 requests per second * 2 seconds = 200 requests total
      timeUnit: '1s',
      duration: '2s',
      preAllocatedVUs: 20,
      maxVUs: 100,
    },
  },
};

export default function () {
  const url = 'http://localhost:8080/api/login';
  const params = {
    headers: {
      'X-Client-Id': 'k6-burst-client',
    },
  };
  
  const res = http.get(url, params);
  
  check(res, {
    'status is 200 or 429': (r) => r.status === 200 || r.status === 429,
  });
}
