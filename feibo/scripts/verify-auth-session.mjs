import assert from 'node:assert/strict';
import { readFile } from 'node:fs/promises';
import { Buffer } from 'node:buffer';
import ts from 'typescript';

const sessionSource = await readFile(new URL('../src/services/authSession.ts', import.meta.url), 'utf8');
const transpiled = ts.transpileModule(sessionSource, {
  compilerOptions: {
    module: ts.ModuleKind.ES2022,
    target: ts.ScriptTarget.ES2022,
  },
}).outputText;
const sessionModule = await import(`data:text/javascript;base64,${Buffer.from(transpiled).toString('base64')}`);

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

const deferred = () => {
  let resolve;
  let reject;
  const promise = new Promise((resolvePromise, rejectPromise) => {
    resolve = resolvePromise;
    reject = rejectPromise;
  });
  return { promise, resolve, reject };
};

{
  const storage = new MemoryStorage();
  let redirects = 0;
  const manager = sessionModule.createAuthSessionManager({
    storage,
    redirectToLogin: () => { redirects += 1; },
  });
  let sessionResets = 0;
  const unsubscribeReset = manager.subscribeToResets(() => { sessionResets += 1; });

  manager.replaceTokens('access-a', 'refresh-a');
  assert.equal(sessionResets, 1, '新登录必须关闭旧会话持有的长连接');
  const sessionA = manager.capture();
  const refreshResponse = deferred();
  let refreshCalls = 0;
  const refresh = async () => {
    refreshCalls += 1;
    return refreshResponse.promise;
  };

  const first = manager.refreshOnce(sessionA, refresh);
  const second = manager.refreshOnce(sessionA, refresh);
  await Promise.resolve();
  assert.equal(refreshCalls, 1, '并发 401 必须共用一次 refresh');
  refreshResponse.resolve({ accessToken: 'access-a2', refreshToken: 'refresh-a2' });
  assert.deepEqual(await Promise.all([first, second]), ['access-a2', 'access-a2']);
  assert.equal(storage.getItem('access-token'), 'access-a2');
  assert.equal(storage.getItem('refresh-token'), 'refresh-a2');
  assert.equal(manager.isCurrent(sessionA), true, 'refresh轮换后仍是同一逻辑会话');
  assert.equal(
    await manager.refreshOnce(sessionA, async () => {
      throw new Error('已轮换会话不得重放旧refresh token');
    }),
    'access-a2',
  );

  const staleRefresh = deferred();
  const stalePromise = manager.refreshOnce(manager.capture(), () => staleRefresh.promise);
  manager.replaceTokens('access-b', 'refresh-b');
  assert.equal(sessionResets, 2, '账号切换必须触发长连接清理');
  staleRefresh.resolve({
    accessToken: 'access-from-old-session',
    refreshToken: 'refresh-from-old-session',
  });
  await assert.rejects(stalePromise, sessionModule.AuthSessionStaleError);
  assert.equal(storage.getItem('access-token'), 'access-b', '旧 refresh 不得覆盖新登录');
  assert.equal(manager.expire(sessionA), false, '旧请求不得清理新会话');
  assert.equal(storage.getItem('refresh-token'), 'refresh-b');
  assert.equal(redirects, 0);

  const sessionB = manager.capture();
  const transientFailure = new Error('network down');
  await assert.rejects(
    manager.refreshOnce(sessionB, async () => { throw transientFailure; }),
    transientFailure,
  );
  assert.equal(storage.getItem('refresh-token'), 'refresh-b', '网络异常不得清理会话');

  storage.setItem('user-info', '{"userId":"b"}');
  storage.setItem('user-auth', '{"isLoggedIn":true}');
  assert.equal(manager.expire(sessionB), true);
  assert.equal(storage.getItem('access-token'), null);
  assert.equal(storage.getItem('refresh-token'), null);
  assert.equal(storage.getItem('user-info'), null);
  assert.equal(storage.getItem('user-auth'), null);
  assert.equal(redirects, 1, '会话过期只跳转一次');
  assert.equal(sessionResets, 3, '会话过期必须触发长连接清理');
  unsubscribeReset();
}

for (const code of ['0009', '0014', 'E0001', 'E0014', ' e0014 ']) {
  assert.equal(sessionModule.isSessionExpiredCode(code), true, `应识别过期码 ${code}`);
}
for (const code of ['0000', 'E0013', '403', undefined]) {
  assert.equal(sessionModule.isSessionExpiredCode(code), false, `不应将 ${code} 当作会话过期`);
}

const authApiSource = await readFile(new URL('../src/services/authApi.ts', import.meta.url), 'utf8');
const backendClientSource = await readFile(
  new URL('../src/services/http/backendClient.ts', import.meta.url),
  'utf8',
);
const rawRequestSource = await readFile(
  new URL('../src/services/http/authenticatedRawRequest.ts', import.meta.url),
  'utf8',
);
const eventSourceSource = await readFile(
  new URL('../src/services/http/authenticatedEventSource.ts', import.meta.url),
  'utf8',
);
const requestScopeSource = await readFile(
  new URL('../src/services/http/sessionRequestScope.ts', import.meta.url),
  'utf8',
);
const requestScopeTranspiled = ts.transpileModule(requestScopeSource, {
  compilerOptions: {
    module: ts.ModuleKind.ES2022,
    target: ts.ScriptTarget.ES2022,
  },
}).outputText;
const requestScopeModule = await import(
  `data:text/javascript;base64,${Buffer.from(requestScopeTranspiled).toString('base64')}`
);

{
  const baseOptions = {
    method: 'GET',
    dedupeKey: 'digital-human:works:first:20',
    cancelKey: 'digital-human:works:list',
    reuseInflight: true,
  };
  const sessionA = {
    generation: 1,
    sessionId: 'session-a',
    accessToken: 'access-a',
    refreshToken: 'refresh-a',
  };
  const sessionB = {
    ...sessionA,
    generation: 2,
    sessionId: 'session-b',
    accessToken: 'access-b',
    refreshToken: 'refresh-b',
  };
  const scopedA1 = requestScopeModule.scopeRequestOptionsToSession(baseOptions, sessionA);
  const scopedA2 = requestScopeModule.scopeRequestOptionsToSession(baseOptions, sessionA);
  const scopedB = requestScopeModule.scopeRequestOptionsToSession(baseOptions, sessionB);

  assert.equal(scopedA1.dedupeKey, scopedA2.dedupeKey, '同一登录仍应复用请求');
  assert.equal(scopedA1.cancelKey, scopedA2.cancelKey, '同一登录仍应共享取消键');
  assert.notEqual(scopedA1.dedupeKey, scopedB.dedupeKey, '新登录不得复用旧账号响应');
  assert.notEqual(scopedA1.cancelKey, scopedB.cancelKey, '新登录不得取消旧账号请求');
  assert.equal(baseOptions.dedupeKey, 'digital-human:works:first:20', '不得修改调用者配置');

  const legacyA = requestScopeModule.scopeRequestOptionsToSession(baseOptions, {
    ...sessionA,
    sessionId: null,
  });
  const legacyB = requestScopeModule.scopeRequestOptionsToSession(baseOptions, {
    ...sessionB,
    sessionId: null,
  });
  assert.notEqual(legacyA.dedupeKey, legacyB.dedupeKey, '旧会话也必须按 generation 隔离');
}

for (const [name, source] of [
  ['authApi', authApiSource],
  ['backendClient', backendClientSource],
]) {
  assert.match(source, /headers\.set\('Authorization'/, `${name} 必须最终写入认证头`);
  assert.doesNotMatch(
    source,
    /requestJson[\s\S]{0,300}\{\s*headers,\s*\.\.\.options/,
    `${name} 不得让 options.headers 覆盖最终请求头`,
  );
  assert.match(source, /retryCount:\s*retryCount \+ 1|retryCount \+ 1/);
  assert.match(source, /authSession\.expire\(requestSession\)/);
}

assert.match(sessionSource, /window\.location\.replace\('\/login'\)/);
assert.match(sessionSource, /storedRefreshToken !== snapshot\.refreshToken/);
assert.match(backendClientSource, /scopeRequestOptionsToSession\(options, requestSession\)/);
assert.match(authApiSource, /void requestJson<ApiResponse<void>>/);
assert.match(authApiSource, /authSession\.subscribeToResets\(destroyWebSocketService\)/);
assert.match(authApiSource, /'\/auth\/refresh\/rotate'/);
assert.match(authApiSource, /method:\s*'POST'/);
assert.doesNotMatch(authApiSource, /apiRequest<string>\('\/auth\/refresh'/);
assert.match(authApiSource, /recoverUnauthorizedSession\(requestSession\)/);
assert.match(backendClientSource, /recoverUnauthorizedSession\(requestSession\)/);
assert.match(rawRequestSource, /recoverUnauthorizedSession\(requestSession\)/);
assert.match(eventSourceSource, /recoverUnauthorizedSession\(attemptSession\)/);
assert.match(
  authApiSource,
  /error\.status >= 200[\s\S]{0,160}error\.status < 300[\s\S]{0,160}isSessionExpiredCode\(error\.code\)/,
  '仅HTTP 2xx业务过期码或HTTP 401可判定refresh失效，403/5xx必须保留会话',
);

console.log('auth session contract: PASS');
