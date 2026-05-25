import http from 'k6/http';
import { check, sleep } from 'k6';
import { login, getAuthHeaders, registerUser, createDuoRoom } from './helpers.js';

export let options = {
  stages: [
    { target: 50, duration: '30s' },
    { target: 200, duration: '60s' },
    { target: 500, duration: '15s' },
    { target: 0, duration: '30s' },
  ],
  thresholds: {
    http_req_duration: ['p(95)<5000', 'p(99)<10000'],
    http_req_failed: ['rate<0.05'],
  },
  tags: {
    test: 'stress-chat',
    component: 'chat-api',
  },
};

const BASE_URL = __ENV.BASE_URL || 'http://localhost:8080';

export function setup() {
  registerUser('k6_stress_a', 'password123');
  registerUser('k6_stress_b', 'password456');
  let tokenA = login('k6_stress_a', 'password123');
  let tokenB = login('k6_stress_b', 'password456');
  let roomId = createDuoRoom(tokenA, tokenB, 'k6_stress_a', 'k6_stress_b');
  return { tokenA, tokenB, roomId };
}

export default function (data) {
  const token = data.tokenA;
  const headers = getAuthHeaders(token);
  const roomId = data.roomId;

  if (!roomId) {
    sleep(1);
    return;
  }

  let r = http.get(`${BASE_URL}/api/v1/chatrooms/`, { headers });
  check(r, { 'chatrooms 200': r => r.status === 200 });
  sleep(0.5);

  r = http.get(`${BASE_URL}/api/v1/messages/?room=${roomId}&page=1`, { headers });
  check(r, { 'messages 200': r => r.status === 200 });
  sleep(1);
}