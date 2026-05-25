import ws from 'k6/ws';
import { check, sleep } from 'k6';
import { Trend } from 'k6/metrics';
import { login, registerUser } from './helpers.js';

const wsConnectTrend = new Trend('ws_connect_duration');
const stompConnectTrend = new Trend('stomp_connect_duration');

export let options = {
  stages: [
    { target: 3, duration: '10s' },
    { target: 5, duration: '20s' },
    { target: 0, duration: '10s' },
  ],
  thresholds: {
    ws_connect_duration: ['p(95)<2000'],
    stomp_connect_duration: ['p(95)<1000'],
  },
  tags: {
    test: 'latency-websocket',
    component: 'chat-ws',
  },
};

const BASE_URL = __ENV.BASE_URL || 'http://localhost:8080';

export function setup() {
  registerUser('k6_ws_user', 'password123');
  return login('k6_ws_user', 'password123');
}

export default function (token) {
  const wsUrl = `ws://localhost:8080/ws`;
  let connectStart = Date.now();

  let res = ws.connect(wsUrl, {}, function (socket) {
    wsConnectTrend.add(Date.now() - connectStart);

    socket.on('open', function () {
      let stompStart = Date.now();
      let stompFrame = `CONNECT\nAuthorization:Bearer ${token}\naccept-version:1.2\nhost:localhost\n\n\u0000`;
      socket.send(stompFrame);

      socket.on('message', function (data) {
        if (data.startsWith('CONNECTED')) {
          stompConnectTrend.add(Date.now() - stompStart);

          let subFrame = `SUBSCRIBE\nid:sub-0\ndestination:/topic/presence\n\n\u0000`;
          socket.send(subFrame);
        }
      });
    });

    socket.on('close', function () {});
    socket.setTimeout(function () { socket.close(); }, 5000);
  });

  check(res, { 'websocket connected': r => r && r.status === 101 });
  sleep(2);
}