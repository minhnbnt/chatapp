import http from 'k6/http';
import { check, sleep } from 'k6';
import { Trend } from 'k6/metrics';
import { login, getAuthHeaders, registerUser, createDuoRoom } from './helpers.js';

const listRoomsTrend = new Trend('list_rooms_duration');
const getMessagesTrend = new Trend('get_messages_duration');
const typingTrend = new Trend('typing_duration');
const readTrend = new Trend('read_duration');

export let options = {
  stages: [
    { target: 5, duration: '10s' },
    { target: 10, duration: '20s' },
    { target: 10, duration: '30s' },
    { target: 0, duration: '10s' },
  ],
  thresholds: {
    http_req_duration: ['p(95)<3000', 'p(99)<5000'],
    http_req_failed: ['rate<0.05'],
  },
  tags: {
    test: 'latency-http',
    component: 'chat-api',
  },
};

const BASE_URL = __ENV.BASE_URL || 'http://localhost:8080';

export function setup() {
  registerUser('k6_user_a', 'password123');
  registerUser('k6_user_b', 'password456');

  let tokenA = login('k6_user_a', 'password123');
  let tokenB = login('k6_user_b', 'password456');

  let roomId = createDuoRoom(tokenA, tokenB, 'k6_user_a', 'k6_user_b');

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
  check(r, { 'list rooms 200': r => r.status === 200 });
  listRoomsTrend.add(r.timings.duration);
  sleep(0.3);

  r = http.get(`${BASE_URL}/api/v1/messages/?room=${roomId}&page=1`, { headers });
  check(r, { 'get messages 200': r => r.status === 200 });
  getMessagesTrend.add(r.timings.duration);
  sleep(0.5);

  r = http.post(`${BASE_URL}/api/v1/messages/typing?room=${roomId}`,
    JSON.stringify({ typing: true }),
    { headers }
  );
  check(r, { 'typing 204': r => r.status === 204 });
  typingTrend.add(r.timings.duration);
  sleep(0.2);

  r = http.post(`${BASE_URL}/api/v1/messages/read?room=${roomId}`, null, {
    headers: { 'Authorization': `Bearer ${token}` },
  });
  check(r, { 'read 204': r => r.status === 204 });
  readTrend.add(r.timings.duration);
  sleep(0.5);
}