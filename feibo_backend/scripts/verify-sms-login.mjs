import assert from 'node:assert/strict';
import { readFile } from 'node:fs/promises';
import ts from 'typescript';

// Run the real auth service; replace only transport and browser storage.
const source = await readFile(new URL('../src/services/auth.ts', import.meta.url), 'utf8');
const globals = ['localStorage', 'window', 'fetch', '__inflightAuthReqs', '__recentAuthCache'];
const original = new Map(globals.map(key => [key, Object.getOwnPropertyDescriptor(globalThis, key)]));
const values = new Map();
globalThis.localStorage = {
  getItem: key => values.get(key) ?? null,
  setItem: (key, value) => values.set(key, String(value)),
  removeItem: key => values.delete(key),
};
const redirects = [];
globalThis.window = { location: { pathname: '/login', replace: value => redirects.push(value) } };
let respond;
const requests = [];
globalThis.fetch = async (url, options) => {
  requests.push({ url, options });
  return respond(url, options);
};
const executable = source
  .replace("import { API_CONFIG } from '../config/api';", "const API_CONFIG = { BASE_URL: 'https://test.invalid', TIMEOUT: 10000, ENDPOINTS: { LOGIN: '/api/v1/auth/login/password' } };")
  .replace("import { v4 as uuidv4 } from 'uuid';", "let sequence = 0; const uuidv4 = () => `test-idempotency-${++sequence}`;");
const compiled = ts.transpileModule(executable, {
  compilerOptions: { module: ts.ModuleKind.ES2022, target: ts.ScriptTarget.ES2022 },
}).outputText;
const { AuthService, authenticatedRequest } = await import(`data:text/javascript;base64,${Buffer.from(compiled).toString('base64')}`);
const response = (data, code = '0000', info = 'OK', status = 200) => new Response(JSON.stringify({ code, info, data }), { status });
const user = role => ({
  userId: '2077567890123456789', nickName: '合成管理员', phoneNumber: '13800000000', gender: '未知', avatar: '',
  roles: [{ name: role }], permissions: ['system.monitor'], accessToken: 'synthetic-access', refreshToken: 'synthetic-refresh',
});
const credentials = { phoneNumber: '13800000000', clientFingerprint: 'ignored-caller-value' };
const modes = [
  { name: 'password', login: () => AuthService.login({ ...credentials, password: 'synthetic-password' }), field: 'password', value: 'synthetic-password', absent: 'verificationCode' },
  { name: 'verification-code', login: () => AuthService.smsLogin({ ...credentials, verificationCode: '123456' }), field: 'verificationCode', value: '123456', absent: 'password' },
];
const reset = () => {
  values.clear();
  localStorage.setItem('client-fingerprint', 'persisted-device');
  requests.length = 0;
  redirects.length = 0;
  globalThis.__inflightAuthReqs = new Map([['old', Promise.resolve('old')]]);
  globalThis.__recentAuthCache = new Map([['old', { data: 'old' }]]);
};
let checks = 0;
try {
  for (const mode of modes) {
    for (const role of ['ADMIN', 'SUPER_ADMIN', 'SUPERADMIN', ' superadmin ']) {
      reset();
      const account = user(role);
      respond = () => response(account);
      const result = await mode.login();
      assert.equal(result.userId, account.userId);
      assert.equal(requests.length, 1);
      const request = requests[0];
      assert.equal(request.url, `https://test.invalid/api/v1/auth/login/${mode.name}`);
      assert.equal(request.options.method, 'POST');
      const body = JSON.parse(request.options.body);
      assert.deepEqual(body, { phoneNumber: credentials.phoneNumber, clientFingerprint: 'persisted-device', [mode.field]: mode.value, loginType: 2 });
      assert.equal(Object.hasOwn(body, mode.absent), false);
      assert.ok(request.options.headers['Idempotency-Token']);
      assert.equal(localStorage.getItem('access-token'), account.accessToken);
      assert.equal(localStorage.getItem('refresh-token'), account.refreshToken);
      assert.ok(localStorage.getItem('auth-session-id'));
      const saved = JSON.parse(localStorage.getItem('userInfo'));
      assert.equal(saved.userId, account.userId);
      assert.deepEqual(saved.permissions, account.permissions);
      assert.equal(Object.hasOwn(saved, 'accessToken'), false);
      assert.equal(Object.hasOwn(saved, 'refreshToken'), false);
      assert.equal(AuthService.isAdmin(), true);
      assert.equal(AuthService.isSuperAdmin(), role.trim() !== 'ADMIN');
      assert.equal(globalThis.__inflightAuthReqs.size, 0);
      assert.equal(globalThis.__recentAuthCache.size, 0);
      checks += 1;
    }
    for (const role of ['USER', 'ORG_ADMIN', undefined]) {
      reset(); respond = () => response(user(role));
      await assert.rejects(mode.login(), /您没有管理员权限/);
      assert.equal(localStorage.getItem('access-token'), null);
      assert.equal(localStorage.getItem('refresh-token'), null);
      assert.equal(localStorage.getItem('userInfo'), null);
      checks += 1;
    }
    reset(); respond = () => response(null, '0006', '手机号或验证码错误');
    await assert.rejects(mode.login(), /手机号或验证码错误/);
    assert.equal(localStorage.getItem('access-token'), null);
    checks += 1;
  }

  reset(); respond = () => response(null);
  await AuthService.sendLoginCode(' 13800000000 ');
  assert.equal(requests[0].url, 'https://test.invalid/api/v1/auth/login/send?phoneNumber=13800000000');
  assert.equal(requests[0].options.method, 'POST');
  assert.equal(requests[0].options.body, undefined);
  assert.equal(localStorage.getItem('access-token'), null);
  assert.equal(localStorage.getItem('client-fingerprint'), 'persisted-device');
  checks += 1;

  for (const action of [() => AuthService.sendLoginCode(credentials.phoneNumber), ...modes.map(mode => mode.login)]) {
    for (const [status, message] of [[429, '操作过于频繁，请稍后重试'], [503, '登录服务暂时不可用，请稍后重试']]) {
      reset(); respond = () => response(null, 'private-error', 'must not display raw upstream detail', status);
      await assert.rejects(action(), error => error.message === message);
      assert.equal(localStorage.getItem('access-token'), null);
      assert.deepEqual(redirects, []);
      checks += 1;
    }
    reset(); respond = () => { throw new TypeError('Failed to fetch'); };
    await assert.rejects(action(), /网络连接失败，请检查网络后重试/);
    checks += 1;
  }

  // A late read from the previous session must not refill caches after SMS login.
  reset(); respond = () => response(user('ADMIN'));
  await modes[0].login();
  const oldSessionId = localStorage.getItem('auth-session-id');
  let finishOldRead;
  respond = () => new Promise(resolve => { finishOldRead = resolve; });
  const oldRead = authenticatedRequest('/previous-account');
  const staleRejection = assert.rejects(oldRead, /登录状态已更新/);
  respond = () => response(user('SUPER_ADMIN'));
  await modes[1].login();
  assert.notEqual(localStorage.getItem('auth-session-id'), oldSessionId);
  finishOldRead(response('old-data'));
  await staleRejection;
  assert.equal(globalThis.__recentAuthCache.size, 0);
  assert.equal(AuthService.isSuperAdmin(), true);
  checks += 1;

  console.log(`PASS admin password/SMS login: ${checks} scenarios; role gate, loginType, long IDs, shared session/cache, SMS send, rate limits and network failures`);
} finally {
  for (const [key, descriptor] of original) {
    if (descriptor) Object.defineProperty(globalThis, key, descriptor);
    else delete globalThis[key];
  }
}
