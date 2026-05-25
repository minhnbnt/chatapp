import http from 'k6/http';
import { check } from 'k6';

const BASE_URL = __ENV.BASE_URL || 'http://localhost:8080';

export function login(username, password) {
  let res = http.post(`${BASE_URL}/api/v1/users/token/`,
    JSON.stringify({ username, password }),
    { headers: { 'Content-Type': 'application/json' } }
  );
  check(res, { 'login 200': r => r.status === 200 });
  return res.json().access;
}

export function getAuthHeaders(token) {
  return {
    'Authorization': `Bearer ${token}`,
    'Content-Type': 'application/json',
  };
}

export function registerUser(username, password) {
  let res = http.post(`${BASE_URL}/api/v1/users/register/`,
    JSON.stringify({ username, password }),
    { headers: { 'Content-Type': 'application/json' } }
  );
  return res.status === 201 || res.status === 409;
}

export function createDuoRoom(tokenA, tokenB, userA, userB) {
  let res = http.post(`${BASE_URL}/api/v1/invitations/`,
    JSON.stringify({ receiverUserName: userB }),
    { headers: getAuthHeaders(tokenA) }
  );
  check(res, { 'invite sent': r => r.status === 201 });

  res = http.get(`${BASE_URL}/api/v1/invitations/`,
    { headers: getAuthHeaders(tokenB) }
  );
  check(res, { 'list invitations': r => r.status === 200 });
  let invitations = res.json();
  if (!invitations || invitations.length === 0) return null;
  let invId = invitations[0].id;

  res = http.patch(`${BASE_URL}/api/v1/invitations/${invId}`,
    JSON.stringify({ accept: true }),
    { headers: getAuthHeaders(tokenB) }
  );
  check(res, { 'accept invitation': r => r.status === 204 });

  res = http.get(`${BASE_URL}/api/v1/chatrooms/`,
    { headers: getAuthHeaders(tokenA) }
  );
  check(res, { 'list rooms after accept': r => r.status === 200 });
  let rooms = res.json();
  if (!rooms || rooms.length === 0) return null;
  return rooms[0].id;
}

export function sendSampleMessage(token, roomId) {
  let res = http.post(`${BASE_URL}/api/v1/messages/?room=${roomId}`,
    { message: 'Hello from k6 test' },
    { headers: { 'Authorization': `Bearer ${token}` } }
  );
  check(res, { 'send message': r => r.status === 201 });
}