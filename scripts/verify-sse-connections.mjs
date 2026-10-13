import assert from 'node:assert/strict';
import { setTimeout as delay } from 'node:timers/promises';

// Read-only verification against the local development server; no accounts/data are created.
const baseUrl = process.env.YUME_API_BASE_URL || 'http://127.0.0.1:8080';
assert(['localhost', '127.0.0.1', '[::1]'].includes(new URL(baseUrl).hostname), 'Use a local development server.');
const controllers = [];
const readers = [];
let token;
let maxReadMillis = 0;

async function json(path, options = {}) {
  const started = performance.now();
  const response = await fetch(new URL(path, baseUrl), {
    ...options,
    signal: AbortSignal.timeout(5000),
    headers: { 'Content-Type': 'application/json', ...(token ? { Authorization: `Bearer ${token}` } : {}) },
  });
  assert.equal(response.status, 200, `${path}: HTTP ${response.status}`);
  const body = await response.json();
  maxReadMillis = Math.max(maxReadMillis, performance.now() - started);
  return body;
}

try {
  const login = await json('/api/auth/login', {
    method: 'POST', body: JSON.stringify({ username: 'test', password: 'test' }),
  });
  token = login.data.token;
  assert(token, 'Test account login did not return a token.');
  const plans = await json('/api/plans?size=1');
  const planId = plans.data.content[0]?.id;
  assert(planId, 'The test account needs an existing plan.');

  for (let i = 0; i < 12; i++) {
    const controller = new AbortController();
    controllers.push(controller);
    const deadline = setTimeout(() => controller.abort(), 5000);
    try {
      const response = await fetch(new URL(`/api/plans/${planId}/events`, baseUrl), {
        headers: { Authorization: `Bearer ${token}`, Accept: 'text/event-stream' }, signal: controller.signal,
      });
      assert.equal(response.status, 200);
      const reader = response.body.getReader();
      readers.push(reader);
      const first = await reader.read();
      assert.equal(first.done, false);
      assert.match(new TextDecoder().decode(first.value), /event:ready/);
    } finally {
      clearTimeout(deadline);
    }
  }
  console.log('Opened 12 SSE streams; checking normal reads while all streams remain open.');
  for (let round = 0; round < 2; round++) {
    assert.equal((await json('/actuator/health')).status, 'UP');
    for (let i = 0; i < 10; i++) assert.equal((await json(`/api/plans/${planId}`)).success, true);
    if (round === 0) await delay(35000); // Beyond the configured 30-second leak warning threshold.
  }
  console.log(JSON.stringify({ streams: 12, normalReads: 20, heldMillis: 35000, maxReadMillis: Math.round(maxReadMillis) }));
} finally {
  controllers.forEach(controller => controller.abort());
  await Promise.allSettled(readers.map(async reader => {
    try { await reader.cancel(); } finally { reader.releaseLock(); }
  }));
  token = undefined;
}
assert.equal((await json('/actuator/health')).status, 'UP');
console.log('All test streams closed; database health is UP.');
