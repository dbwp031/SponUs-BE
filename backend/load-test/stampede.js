import http from 'k6/http';
import { check } from 'k6';

// 특정 공고 상세를 고정 RPS 로 조회하여 TTL 만료 시점의 cache stampede 를 유발한다.
// 사용: k6 run -e BASE_URL=http://localhost:8088 -e ANN_ID=1 load-test/stampede.js

const BASE = __ENV.BASE_URL || 'http://localhost:8088';
const ANN_ID = __ENV.ANN_ID || '1';

export const options = {
  scenarios: {
    stampede: {
      executor: 'constant-arrival-rate',
      rate: Number(__ENV.RATE || 300),   // 초당 요청 수
      timeUnit: '1s',
      duration: __ENV.DURATION || '40s', // TTL 10s 기준 약 4회 만료 통과
      preAllocatedVUs: Number(__ENV.PRE_VUS || 200),
      maxVUs: Number(__ENV.MAX_VUS || 600),
    },
  },
  thresholds: {
    http_req_failed: ['rate<0.05'],
  },
};

export default function () {
  const res = http.get(`${BASE}/api/v2/announcements/${ANN_ID}`);
  check(res, { 'status is 200': (r) => r.status === 200 });
}
