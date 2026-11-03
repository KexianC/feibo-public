import assert from 'node:assert/strict';
import { Buffer } from 'node:buffer';
import { readFile } from 'node:fs/promises';
import ts from 'typescript';

// Execute the real request implementation with only network, storage and timers replaced.
const source = await readFile(new URL('../src/services/auth.ts', import.meta.url), 'utf8');
const originalGlobals = new Map(['localStorage', 'window', 'fetch', '__authTestTimers', '__inflightAuthReqs', '__recentAuthCache']
  .map((key) => [key, Object.getOwnPropertyDescriptor(globalThis, key)]));
const values = new Map();
globalThis.localStorage = {
  getItem: (key) => values.get(key) ?? null,
  setItem: (key, value) => values.set(key, String(value)),
  removeItem: (key) => values.delete(key),
};
const redirects = [];
globalThis.window = { location: { pathname: '/dashboard', replace: (path) => redirects.push(path) } };
let fetchImplementation = () => { throw new Error('Unconfigured fetch'); };
globalThis.fetch = (...args) => fetchImplementation(...args);
const timers = [];
globalThis.__authTestTimers = {
  setTimeout(callback, delay) {
    const timer = { callback, delay, cleared: false };
    timers.push(timer);
    return timer;
  },
  clearTimeout(timer) { timer.cleared = true; },
};
const executable = source
  .replace("import { API_CONFIG } from '../config/api';", "const API_CONFIG = { BASE_URL: 'https://test.invalid', TIMEOUT: 10000, ENDPOINTS: { REFRESH_TOKEN: '/api/v1/auth/refresh/rotate' } };")
  .replace("import { v4 as uuidv4 } from 'uuid';", "const uuidv4 = () => 'test-idempotency-token'; const { setTimeout, clearTimeout } = globalThis.__authTestTimers;");
const compiled = ts.transpileModule(executable, { compilerOptions: { module: ts.ModuleKind.ES2022, target: ts.ScriptTarget.ES2022 } }).outputText;
const { authenticatedRequest, AuthService } = await import(`data:text/javascript;base64,${Buffer.from(compiled).toString('base64')}`);
const respond = (data, status = 200) => new Response(JSON.stringify({ code: 200, data }), { status, headers: { 'content-type': 'application/json' } });
const deferred = () => {
  let resolve;
  const promise = new Promise((done) => { resolve = done; });
  return { promise, resolve };
};
const login = (id) => {
  localStorage.setItem('access-token', `${id}-access`);
  localStorage.setItem('refresh-token', `${id}-refresh`);
  localStorage.setItem('auth-session-id', id);
  localStorage.setItem('client-fingerprint', 'test-fingerprint');
  localStorage.setItem('userInfo', JSON.stringify({ roles: [{ name: 'ADMIN' }] }));
};

try {
  login('first');
  let fetchOptions;
  fetchImplementation = async (_url, options) => { fetchOptions = options; return respond('default'); };
  assert.equal((await authenticatedRequest('/timeout-default')).data, 'default');
  assert.equal(timers.at(-1).delay, 10000);
  assert.equal(timers.at(-1).cleared, true);
  assert.equal(Object.hasOwn(fetchOptions, 'timeoutMs'), false);
  await authenticatedRequest('/timeout-upload', { method: 'POST', body: 'blob', timeoutMs: 120000 });
  assert.equal(timers.at(-1).delay, 120000);
  assert.equal(timers.at(-1).cleared, true);
  assert.equal(fetchOptions.body, 'blob');
  assert.equal(Object.hasOwn(fetchOptions, 'timeoutMs'), false, 'Custom options must not be passed to fetch');
  console.log('PASS default and upload timeouts, cleanup, fetch option isolation');

  fetchImplementation = (_url, options) => new Promise((_resolve, reject) => {
    options.signal.addEventListener('abort', () => reject(new Error('test-request-aborted')), { once: true });
  });
  const timedRequest = authenticatedRequest('/timeout-abort', { timeoutMs: 120000 });
  const timeoutRejection = assert.rejects(timedRequest, /test-request-aborted/);
  const abortTimer = timers.at(-1);
  assert.equal(abortTimer.delay, 120000);
  abortTimer.callback();
  await timeoutRejection;
  assert.equal(abortTimer.cleared, true);

  let fetchCalls = 0;
  fetchImplementation = async () => { fetchCalls += 1; return respond('should not run'); };
  for (const timeoutMs of [0, -1, NaN, Infinity, 300001]) {
    await assert.rejects(authenticatedRequest(`/invalid-${timeoutMs}`, { timeoutMs }), /超时时间/);
  }
  assert.equal(fetchCalls, 0);
  console.log('PASS timer abort and invalid timeout rejection');

  let value = 'before';
  let readCalls = 0;
  fetchImplementation = async (_url, options) => {
    if (options.method === 'POST') { value = 'after'; return respond(true); }
    readCalls += 1;
    return respond(value);
  };
  assert.equal((await authenticatedRequest('/items')).data, 'before');
  assert.equal((await authenticatedRequest('/items')).data, 'before');
  assert.equal(readCalls, 1, 'Reads still use the short recent cache');
  await authenticatedRequest('/items/save', { method: 'POST' });
  assert.equal((await authenticatedRequest('/items')).data, 'after');
  assert.equal(readCalls, 2, 'The immediate reload after a write must go to the server');
  console.log('PASS mutation invalidates recent reads');

  const oldRead = deferred();
  const newerRead = deferred();
  readCalls = 0;
  fetchImplementation = (_url, options) => {
    if (options.method === 'PUT') return Promise.resolve(respond(true));
    readCalls += 1;
    return readCalls === 1 ? oldRead.promise : newerRead.promise;
  };
  const oldRequest = authenticatedRequest('/parallel');
  await authenticatedRequest('/parallel/save', { method: 'PUT' });
  const newRequest = authenticatedRequest('/parallel');
  assert.equal(readCalls, 2, 'The old inflight read must not be reused after a mutation');
  newerRead.resolve(respond('new'));
  assert.equal((await newRequest).data, 'new');
  oldRead.resolve(respond('old'));
  assert.equal((await oldRequest).data, 'old');
  assert.equal((await authenticatedRequest('/parallel')).data, 'new', 'Late old results must not replace the fresh cache');
  assert.equal(readCalls, 2);
  console.log('PASS late old GET cannot refill or overwrite the fresh cache');

  // Mutations invalidate twice: at dispatch and completion, including uncertain failures.
  for (const fails of [false, true]) {
    const write = deferred();
    const path = `/pending-write-${fails}`;
    value = 'before';
    readCalls = 0;
    fetchImplementation = (_url, options) => {
      if (options.method === 'PATCH') return write.promise;
      readCalls += 1;
      return Promise.resolve(respond(value));
    };
    await authenticatedRequest(path);
    const mutation = authenticatedRequest(`${path}/save`, { method: 'PATCH' });
    const result = fails ? assert.rejects(mutation, /HTTP error! status: 500/) : mutation;
    assert.equal((await authenticatedRequest(path)).data, 'before');
    assert.equal(readCalls, 2, 'Dispatch must invalidate an existing cache while the write remains pending');
    value = 'after';
    write.resolve(respond(true, fails ? 500 : 200));
    await result;
    assert.equal((await authenticatedRequest(path)).data, 'after');
    assert.equal(readCalls, 3, 'Completion must invalidate reads made during the write, even if its outcome is uncertain');
  }
  console.log('PASS pending and failed mutation completion invalidation');

  // An old completion also must not delete the NEW generation's inflight entry.
  const stale = deferred();
  const fresh = deferred();
  readCalls = 0;
  fetchImplementation = (_url, options) => {
    if (options.method === 'DELETE') return Promise.resolve(respond(true));
    readCalls += 1;
    return readCalls === 1 ? stale.promise : fresh.promise;
  };
  const staleRequest = authenticatedRequest('/inflight-ownership');
  await authenticatedRequest('/inflight-ownership/1', { method: 'DELETE' });
  const freshRequest = authenticatedRequest('/inflight-ownership');
  stale.resolve(new Response(JSON.stringify('old-raw'), { status: 200 }));
  await staleRequest;
  const deduplicatedFresh = authenticatedRequest('/inflight-ownership');
  assert.equal(readCalls, 2, 'Old completion must leave new inflight entries intact');
  fresh.resolve(new Response(JSON.stringify('fresh-raw'), { status: 200 }));
  assert.equal((await freshRequest).data, 'fresh-raw');
  assert.equal((await deduplicatedFresh).data, 'fresh-raw');
  assert.equal((await authenticatedRequest('/inflight-ownership')).data, 'fresh-raw');
  assert.equal(readCalls, 2);
  console.log('PASS inflight ownership and raw-response cache generation');

  const previousSessionRead = deferred();
  readCalls = 0;
  fetchImplementation = () => {
    readCalls += 1;
    return readCalls === 1 ? previousSessionRead.promise : Promise.resolve(respond('second-session'));
  };
  const previousSessionRequest = authenticatedRequest('/session-isolation');
  const previousSessionRejection = assert.rejects(previousSessionRequest);
  login('second');
  assert.equal((await authenticatedRequest('/session-isolation')).data, 'second-session');
  previousSessionRead.resolve(respond('first-session'));
  await previousSessionRejection;
  assert.equal((await authenticatedRequest('/session-isolation')).data, 'second-session');
  assert.equal(readCalls, 2);
  assert.equal(localStorage.getItem('access-token'), 'second-access');
  assert.deepEqual(redirects, [], 'A stale response must not expire the new session');
  AuthService.invalidateSession();
  assert.equal(globalThis.__inflightAuthReqs.size, 0);
  assert.equal(globalThis.__recentAuthCache.size, 0);
  console.log('PASS session identity and explicit logout cache isolation');
} finally {
  for (const [key, descriptor] of originalGlobals) {
    if (descriptor) Object.defineProperty(globalThis, key, descriptor);
    else delete globalThis[key];
  }
}
console.log('Authenticated request cache and timeout regressions: PASS');
