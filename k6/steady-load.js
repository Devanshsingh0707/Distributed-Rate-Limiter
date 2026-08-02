import http from 'k6/http';
import { sleep, check } from 'k6';

export const options = {
  scenarios: {
    constant_request_rate: {
      executor: 'constant-arrival-rate',
      rate: 20, // 20 requests per second
      timeUnit: '1s',
      duration: '30s',
      preAllocatedVUs: 10,
      maxVUs: 50,
    },
  },
};

export default function () {
  const url = 'http://localhost:8080/api/search';
  const params = {
    headers: {
      'X-Client-Id': 'k6-steady-client',
    },
  };
  
  const res = http.get(url, params);
  
  check(res, {
    'status is 200 or 429': (r) => r.status === 200 || r.status === 429,
  });
}
