import assert from 'node:assert/strict';
import { Buffer } from 'node:buffer';
import { readFile } from 'node:fs/promises';
import ts from 'typescript';

const authSource = await readFile(new URL('../src/services/auth.ts', import.meta.url), 'utf8');
const apiSource = await readFile(new URL('../src/config/api.ts', import.meta.url), 'utf8');
const authHookSource = await readFile(new URL('../src/hooks/useAuth.ts', import.meta.url), 'utf8');
const protectedRouteSource = await readFile(new URL('../src/components/ProtectedRoute.tsx', import.meta.url), 'utf8');

assert.match(apiSource, /REFRESH_TOKEN:\s*'\/api\/v1\/auth\/refresh\/rotate'/);
assert.match(authSource, /method:\s*'POST'/);
assert.match(authSource, /cache:\s*'no-store'/);
assert.match(authSource, /const SESSION_ID_KEY = 'auth-session-id'/);
assert.match(authSource, /currentSessionId\(\) === snapshot\.sessionId/);
assert.match(authSource, /storedRefreshToken !== session\.refreshToken/);
assert.match(authSource, /localStorage\.setItem\('refresh-token', response\.data\.refreshToken\)/);
assert.match(authSource, /recoverUnauthorizedSession\(requestSession\)/);
assert.match(authSource, /recoverUnauthorizedSession[\s\S]{0,900}expireSession\(\)/);
assert.match(authHookSource, /isAuthenticated && storedUser && AuthService\.isAdmin\(\)/);
assert.match(authHookSource, /AuthService\.invalidateSession\(\)/);
assert.match(protectedRouteSource, /AuthService\.invalidateSession\(\)/);
assert.doesNotMatch(protectedRouteSource, /权限不足|只有管理员可以访问管理后台/);
assert.match(
  authSource,
  /error\.status >= 200[\s\S]{0,160}error\.status < 300[\s\S]{0,160}SESSION_EXPIRED_CODES\.has/,
  'HTTP 403/5xx即使携带过期码也不得清理本地会话',
);
assert.doesNotMatch(
  authSource,
  /customApiRequest<string>\(API_CONFIG\.ENDPOINTS\.REFRESH_TOKEN/,
  'rotating refresh must not expect the legacy access-token-only response',
);
assert.doesNotMatch(
  authSource,
  /console\.log\([^\n]*(?:登录API响应原始文本|预处理后的JSON|登录API解析后的结果|JSON解析确认)/,
  'login and refresh responses may contain bearer credentials and must never be logged',
);

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

const storage = new MemoryStorage();
const redirects = [];
let fetchImplementation = async () => {
  throw new Error('fetch mock not configured');
};
globalThis.localStorage = storage;
globalThis.window = {
  location: {
    pathname: '/dashboard',
    replace: (value) => redirects.push(value),
  },
};
globalThis.fetch = (...args) => fetchImplementation(...args);

const executableAuthSource = authSource
  .replace(
    "import { API_CONFIG } from '../config/api';",
    "const API_CONFIG = { BASE_URL: 'https://api.hzmwmt.com', TIMEOUT: 10000, ENDPOINTS: { LOGIN: '/api/v1/auth/login/password', LOGOUT: '/api/v1/auth/logout', REFRESH_TOKEN: '/api/v1/auth/refresh/rotate', USER_INFO: '/api/admin/user/current' } };",
  )
  .replace("import { v4 as uuidv4 } from 'uuid';", "const uuidv4 = () => 'contract-idempotency-token';");
const executableAuthJs = ts.transpileModule(executableAuthSource, {
  compilerOptions: {
    module: ts.ModuleKind.ES2022,
    target: ts.ScriptTarget.ES2022,
  },
}).outputText;
const contract = await import(`data:text/javascript;base64,${Buffer.from(executableAuthJs).toString('base64')}`);

try {
  storage.setItem('client-fingerprint', 'admin-fingerprint');
  storage.setItem('access-token', 'admin-access');
  storage.setItem('refresh-token', 'admin-refresh');
  storage.setItem('auth-session-id', 'admin-session');
  storage.setItem('userInfo', JSON.stringify({ roles: [{ name: 'ADMIN' }] }));

  let requestCalls = 0;
  fetchImplementation = async (url) => {
    requestCalls += 1;
    if (String(url).endsWith('/api/v1/auth/refresh/rotate')) {
      return new Response(JSON.stringify({ code: 'E0000', info: 'unavailable' }), {
        status: 503,
        headers: { 'content-type': 'application/json' },
      });
    }
    return new Response(JSON.stringify({ code: 'E0014', info: 'expired' }), {
      status: 401,
      headers: { 'content-type': 'application/json' },
    });
  };

  await assert.rejects(
    contract.authenticatedRequest('/api/admin/health'),
    (error) => error?.message === '登录已过期，请重新登录',
  );
  assert.equal(requestCalls, 2, 'protected 401 must attempt refresh exactly once');
  assert.equal(storage.getItem('access-token'), null);
  assert.equal(storage.getItem('refresh-token'), null);
  assert.equal(redirects.at(-1), '/login');

  // A proactive refresh failure alone is still transient and must preserve the session.
  storage.setItem('access-token', 'proactive-access');
  storage.setItem('refresh-token', 'proactive-refresh');
  storage.setItem('auth-session-id', 'proactive-session');
  await assert.rejects(contract.AuthService.refreshToken(), /HTTP error! status: 503/);
  assert.equal(storage.getItem('access-token'), 'proactive-access');
  assert.equal(storage.getItem('refresh-token'), 'proactive-refresh');
} finally {
  delete globalThis.__inflightAuthReqs;
  delete globalThis.__recentAuthCache;
}

console.log('admin refresh session contract: PASS');
