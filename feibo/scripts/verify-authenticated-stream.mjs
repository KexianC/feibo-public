import assert from 'node:assert/strict';
import { mkdtemp, readFile, readdir, rm } from 'node:fs/promises';
import path from 'node:path';
import { pathToFileURL } from 'node:url';
import { build } from 'esbuild';

class MemoryStorage {
  values = new Map();

  getItem(key) {
    return this.values.get(key) ?? null;
  }

  setItem(key, value) {
    this.values.set(key, String(value));
  }

  removeItem(key) {
    this.values.delete(key);
  }
}

const jsonResponse = (body, status = 200) => new Response(JSON.stringify(body), {
  status,
  headers: { 'content-type': 'application/json' },
});

const streamResponse = (chunks = ['data: ok\n\n']) => new Response(new ReadableStream({
  start(controller) {
    for (const chunk of chunks) {
      controller.enqueue(new TextEncoder().encode(chunk));
    }
    controller.close();
  },
}), {
  status: 200,
  headers: { 'content-type': 'text/event-stream' },
});

const storage = new MemoryStorage();
const redirects = [];
let fetchImplementation = async () => {
  throw new Error('fetch mock not configured');
};

globalThis.localStorage = storage;
globalThis.window = {
  setTimeout,
  clearTimeout,
  location: {
    pathname: '/workspace',
    replace: (value) => redirects.push(value),
  },
};
globalThis.fetch = (...args) => fetchImplementation(...args);

const bundleDirectory = await mkdtemp(path.join(process.cwd(), 'node_modules', '.tmp', 'feibo-auth-stream-'));
const bundlePath = path.join(bundleDirectory, 'contract.mjs');

try {
  await build({
    stdin: {
      contents: [
        "export { authenticatedRawRequest } from './src/services/http/authenticatedRawRequest.ts';",
        "export { openAuthenticatedEventSource } from './src/services/http/authenticatedEventSource.ts';",
        "export { videoRequest } from './src/services/http/videoClient.ts';",
        "export { authenticatedRequest } from './src/services/authApi.ts';",
        "export { authSession, AuthSessionExpiredError } from './src/services/authSession.ts';",
      ].join('\n'),
      resolveDir: process.cwd(),
      sourcefile: 'authenticated-stream-contract-entry.ts',
      loader: 'ts',
    },
    outfile: bundlePath,
    bundle: true,
    format: 'esm',
    platform: 'browser',
    target: 'es2022',
    define: {
      'import.meta.env.VITE_API_BASE_URL': "''",
      'import.meta.env.DEV': 'true',
    },
    logLevel: 'silent',
  });

  const contract = await import(`${pathToFileURL(bundlePath).href}?v=${Date.now()}`);
  storage.setItem('client-fingerprint', 'fingerprint-current');

  // Attack: caller supplies stale auth headers, initial access token receives
  // 401, and the request is retried exactly once with rotated credentials.
  contract.authSession.replaceTokens('access-old', 'refresh-old');
  const refreshCalls = [];
  fetchImplementation = async (url, options = {}) => {
    const call = { url: String(url), options, headers: new Headers(options.headers) };
    refreshCalls.push(call);
    if (refreshCalls.length === 1) {
      return jsonResponse({ code: 'E0014', info: 'expired', data: null }, 401);
    }
    if (String(url).endsWith('/auth/refresh/rotate')) {
      return jsonResponse({
        code: '0000',
        info: 'ok',
        data: { accessToken: 'access-new', refreshToken: 'refresh-new' },
      });
    }
    return streamResponse(['data: first\n\n']);
  };

  const response = await contract.authenticatedRawRequest('/api/v1/ai/symptomAnalysis', {
    method: 'POST',
    headers: {
      Authorization: 'Bearer attacker-stale',
      'client-fingerprint': 'attacker-fingerprint',
      'content-type': 'application/json',
    },
    body: JSON.stringify({ content: 'sensitive input' }),
  });

  assert.equal(refreshCalls.length, 3);
  assert.equal(refreshCalls[0].headers.get('authorization'), 'Bearer access-old');
  assert.equal(refreshCalls[0].headers.get('client-fingerprint'), 'fingerprint-current');
  assert.equal(refreshCalls[1].headers.get('authorization'), 'Bearer refresh-old');
  assert.equal(refreshCalls[2].headers.get('authorization'), 'Bearer access-new');
  assert.equal(
    refreshCalls[0].headers.get('idempotency-token'),
    refreshCalls[2].headers.get('idempotency-token'),
    '401 retry must reuse the same idempotency token',
  );
  assert.equal(response.bodyUsed, false, 'shared auth boundary must not consume an SSE body');
  await response.text();
  assert.equal(refreshCalls.length, 3, 'reading a returned stream must never trigger replay');

  // A session code delivered inside an established event stream is deliberately
  // not replayed by the transport after body consumption starts.
  const postOpenCalls = [];
  fetchImplementation = async (url, options = {}) => {
    postOpenCalls.push({ url: String(url), options });
    return streamResponse(['data: {"code":"E0014"}\n\n']);
  };
  const postOpenResponse = await contract.authenticatedRawRequest('/api/v1/ai/symptomAnalysis', {
    method: 'POST',
    body: '{}',
  });
  await postOpenResponse.text();
  assert.equal(postOpenCalls.length, 1, 'stream data must not be automatically replayed');
  assert.equal(storage.getItem('refresh-token'), 'refresh-new');

  let videoCall;
  fetchImplementation = async (url, options = {}) => {
    videoCall = { url: String(url), headers: new Headers(options.headers) };
    return jsonResponse({ code: 200, msg: 'ok', data: { accountId: 'account-1' } });
  };
  const videoResult = await contract.videoRequest('/video-publish/accounts', {
    headers: { Authorization: 'Bearer stale-video-token' },
  });
  assert.deepEqual(videoResult.data, { accountId: 'account-1' });
  assert.equal(videoCall.headers.get('authorization'), 'Bearer access-new');
  assert.equal(videoCall.headers.get('client-fingerprint'), 'fingerprint-current');

  // Legitimate negative controls: 403, 5xx, and network failures are not proof
  // of expiry, so they preserve the current logical session.
  for (const status of [403, 500]) {
    fetchImplementation = async () => jsonResponse({ code: 'E0014', info: 'rejected' }, status);
    const rejected = await contract.authenticatedRawRequest('/api/asr/stop');
    assert.equal(rejected.status, status);
    assert.equal(storage.getItem('refresh-token'), 'refresh-new');
  }
  const networkFailure = new Error('offline');
  fetchImplementation = async () => { throw networkFailure; };
  await assert.rejects(
    contract.authenticatedRawRequest('/api/asr/stop'),
    networkFailure,
  );
  assert.equal(storage.getItem('refresh-token'), 'refresh-new');

  const callerAbort = new AbortController();
  fetchImplementation = async (_url, options = {}) => new Promise((_resolve, reject) => {
    options.signal.addEventListener('abort', () => reject(options.signal.reason), { once: true });
  });
  const abortedRequest = contract.authenticatedRawRequest('/api/v1/ai/symptomAnalysis', {
    method: 'POST',
    body: '{}',
    signal: callerAbort.signal,
  });
  callerAbort.abort();
  await assert.rejects(abortedRequest, (error) => error?.name === 'AbortError');
  assert.equal(storage.getItem('refresh-token'), 'refresh-new');

  contract.authSession.replaceTokens('stale-access', 'stale-refresh');
  let resolveStaleResponse;
  fetchImplementation = async () => new Promise((resolve) => {
    resolveStaleResponse = resolve;
  });
  const staleRequest = contract.authenticatedRawRequest('/api/asr/stop');
  await Promise.resolve();
  contract.authSession.replaceTokens('new-login-access', 'new-login-refresh');
  resolveStaleResponse(new Response('old session data', { status: 200 }));
  await assert.rejects(staleRequest, (error) => error?.name === 'AuthSessionStaleError');
  assert.equal(storage.getItem('refresh-token'), 'new-login-refresh');

  // A JSON business expiry code is recognized before returning body ownership.
  fetchImplementation = async () => jsonResponse({ code: 'E0014', info: 'login expired' });
  await assert.rejects(
    contract.authenticatedRawRequest('/api/asr/stop'),
    contract.AuthSessionExpiredError,
  );
  assert.equal(storage.getItem('access-token'), null);
  assert.equal(storage.getItem('refresh-token'), null);
  assert.equal(redirects.at(-1), '/login');

  contract.authSession.replaceTokens('twice-access-old', 'twice-refresh-old');
  let twiceCalls = 0;
  fetchImplementation = async (url) => {
    twiceCalls += 1;
    if (String(url).endsWith('/auth/refresh/rotate')) {
      return jsonResponse({
        code: '0000',
        info: 'ok',
        data: { accessToken: 'twice-access-new', refreshToken: 'twice-refresh-new' },
      });
    }
    return jsonResponse({ code: 'E0014', info: 'still expired' }, 401);
  };
  await assert.rejects(
    contract.authenticatedRawRequest('/api/v1/ai/symptomAnalysis', { method: 'POST', body: '{}' }),
    contract.AuthSessionExpiredError,
  );
  assert.equal(twiceCalls, 3, 'HTTP 401 must be retried at most once');
  assert.equal(storage.getItem('refresh-token'), null);

  contract.authSession.replaceTokens('refresh-403-access', 'refresh-403-token');
  let refreshForbiddenCalls = 0;
  fetchImplementation = async (url) => {
    refreshForbiddenCalls += 1;
    if (String(url).endsWith('/auth/refresh/rotate')) {
      return jsonResponse({ code: 'E0014', info: 'forbidden refresh' }, 403);
    }
    return jsonResponse({ code: 'E0014', info: 'expired access' }, 401);
  };
  await assert.rejects(
    contract.authenticatedRawRequest('/api/asr/stop'),
    contract.AuthSessionExpiredError,
  );
  assert.equal(refreshForbiddenCalls, 2);
  assert.equal(storage.getItem('refresh-token'), null);
  assert.equal(redirects.at(-1), '/login');

  // Production regression: a WebSocket ticket request returning 401 followed
  // by a 503 refresh response must leave the broken page and return to login.
  contract.authSession.replaceTokens('ws-access', 'ws-refresh');
  let wsTicketCalls = 0;
  fetchImplementation = async (url) => {
    wsTicketCalls += 1;
    if (String(url).endsWith('/auth/refresh/rotate')) {
      return jsonResponse({ code: 'E0000', info: 'service unavailable', data: null }, 503);
    }
    return jsonResponse({ code: 'E0014', info: 'expired access', data: null }, 401);
  };
  await assert.rejects(
    contract.authenticatedRequest('/ws/ticket', { method: 'POST' }),
    contract.AuthSessionExpiredError,
  );
  assert.equal(wsTicketCalls, 2, '401 recovery must attempt refresh exactly once');
  assert.equal(storage.getItem('access-token'), null);
  assert.equal(storage.getItem('refresh-token'), null);
  assert.equal(redirects.at(-1), '/login');

  // EventSource follows the same pre-open rule: one 401 refresh retry and final
  // auth headers, while a non-auth HTTP error leaves the session intact.
  contract.authSession.replaceTokens('sse-access-old', 'sse-refresh-old');
  const eventCalls = [];
  fetchImplementation = async (url, options = {}) => {
    const call = { url: String(url), options, headers: new Headers(options.headers) };
    eventCalls.push(call);
    if (eventCalls.length === 1) {
      return new Response('unauthorized', { status: 401, headers: { 'content-type': 'text/plain' } });
    }
    if (String(url).endsWith('/auth/refresh/rotate')) {
      return jsonResponse({
        code: '0000',
        info: 'ok',
        data: { accessToken: 'sse-access-new', refreshToken: 'sse-refresh-new' },
      });
    }
    return streamResponse(['data: accepted\n\n']);
  };

  let eventConnection;
  const eventMessage = new Promise((resolve, reject) => {
    const timeout = setTimeout(() => reject(new Error('EventSource contract timed out')), 2_000);
    eventConnection = contract.openAuthenticatedEventSource(
      '/api/v1/video-publish/login?type=1&username=test',
      { headers: { Authorization: 'Bearer stale' } },
      {
        onMessage: (event) => {
          clearTimeout(timeout);
          resolve(event.data);
        },
        onError: (error) => {
          clearTimeout(timeout);
          reject(error instanceof Error ? error : new Error('EventSource error'));
        },
      },
    );
  });
  assert.equal(await eventMessage, 'accepted');
  eventConnection.close();
  assert.equal(eventCalls.length, 3);
  assert.equal(eventCalls[0].headers.get('authorization'), 'Bearer sse-access-old');
  assert.equal(eventCalls[2].headers.get('authorization'), 'Bearer sse-access-new');

  contract.authSession.replaceTokens('forbidden-access', 'forbidden-refresh');
  fetchImplementation = async () => new Response('forbidden', {
    status: 403,
    headers: { 'content-type': 'text/plain' },
  });
  await new Promise((resolve, reject) => {
    const timeout = setTimeout(() => reject(new Error('403 EventSource contract timed out')), 2_000);
    const connection = contract.openAuthenticatedEventSource('/events', {}, {
      onMessage: () => reject(new Error('403 must not open the stream')),
      onError: () => {
        clearTimeout(timeout);
        connection.close();
        resolve();
      },
    });
  });
  assert.equal(storage.getItem('refresh-token'), 'forbidden-refresh');

  const zhibiSource = await readFile('src/components/ZhibiYiyan.tsx', 'utf8');
  const asrSource = await readFile('src/services/asrApi.ts', 'utf8');
  const legacyModelSource = await readFile('src/services/digitalHumanApi.ts', 'utf8');
  const eventSourceSource = await readFile('src/services/http/authenticatedEventSource.ts', 'utf8');
  const videoClientSource = await readFile('src/services/http/videoClient.ts', 'utf8');
  const loginSource = await readFile('src/components/Login.tsx', 'utf8');
  const authApiSource = await readFile('src/services/authApi.ts', 'utf8');

  assert.match(zhibiSource, /authenticatedRawRequest\(url/);
  assert.match(zhibiSource, /isSessionExpiredCode\(jsonData\?\.code\)/);
  assert.match(zhibiSource, /reader\.read\(\)[\s\S]{0,180}authSession\.assertCurrent\(streamSession\)/);
  assert.doesNotMatch(zhibiSource, /\bfetch\s*\(/);
  assert.doesNotMatch(
    zhibiSource,
    /console\.(?:log|warn|error)\([^\n]*(?:requestBody|jsonData\.content|combinedContent|processedData|convertedMessages)/,
    'medical request and response content must not be logged',
  );
  assert.match(asrSource, /authenticatedRawRequest/);
  assert.doesNotMatch(asrSource, /\bfetch\s*\(|getAuthHeaders/);
  assert.match(legacyModelSource, /backendDataRequest<unknown>/);
  assert.doesNotMatch(legacyModelSource, /localStorage\.getItem\('access-token'\)/);
  assert.match(eventSourceSource, /status === 401 && !bodyStarted && retryCount === 0/);
  assert.match(eventSourceSource, /errors after stream consumption/);
  assert.match(eventSourceSource, /candidate\.onmessage[\s\S]{0,240}authSession\.assertCurrent\(logicalSession\)/);
  assert.match(videoClientSource, /authenticatedRawRequest/);
  assert.doesNotMatch(videoClientSource, /getAuthHeaders|\brequestRaw\b|\brequestJson\b/);
  assert.doesNotMatch(loginSource, /mock-(?:access|refresh)-token|handleSkipLogin/);
  assert.doesNotMatch(authApiSource, /export const getAuthHeaders/);

  const sourceFiles = [];
  const visit = async (directory) => {
    for (const entry of await readdir(directory, { withFileTypes: true })) {
      const child = path.join(directory, entry.name);
      if (entry.isDirectory()) {
        await visit(child);
      } else if (/\.(?:ts|tsx)$/.test(entry.name)) {
        sourceFiles.push(child);
      }
    }
  };
  await visit('src');

  const allowedDirectFetchArguments = new Map([
    [path.normalize('src/services/http/client.ts'), null],
    [path.normalize('src/services/digitalHumanApi.ts'), /^(?:['"]https:\/\/openman\.weta365\.com[^'"]*['"]|videoUrl)$/],
    [path.normalize('src/services/digitalHuman/openPlatform.ts'), /^videoUrl$/],
    [path.normalize('src/components/BottomPanel.tsx'), /^audioFile\.url$/],
    [path.normalize('src/components/PublishVideo.tsx'), /^videoUrl$/],
    [path.normalize('src/utils/excelReader.ts'), /^filePath$/],
  ]);

  for (const file of sourceFiles) {
    const source = await readFile(file, 'utf8');
    const directFetches = [...source.matchAll(/\bfetch\s*\(\s*([^,\n)]+)/g)];
    if (directFetches.length === 0) continue;

    const normalizedFile = path.normalize(file);
    assert.ok(
      allowedDirectFetchArguments.has(normalizedFile),
      `unreviewed direct fetch bypass: ${normalizedFile}`,
    );
    const allowedArgument = allowedDirectFetchArguments.get(normalizedFile);
    if (allowedArgument) {
      for (const match of directFetches) {
        assert.match(match[1].trim(), allowedArgument, `unreviewed fetch target in ${normalizedFile}`);
      }
    }
  }

  console.log('authenticated stream contract: PASS');
} finally {
  await rm(bundleDirectory, { recursive: true, force: true });
}
