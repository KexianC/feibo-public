import { API_CONFIG } from '../config/api';
import { v4 as uuidv4 } from 'uuid';

// 基础URL配置
const BASE_URL = API_CONFIG.BASE_URL;

// 登录接口响应结构
interface LoginApiResponse<T = any> {
  code: string;
  info: string;
  data: T;
}

// 其他接口响应结构
interface ApiResponse<T = any> {
  code: number | string;
  msg?: string;
  info?: string;
  data: T;
}

interface RefreshTokenResponse {
  accessToken: string;
  refreshToken: string;
  tokenHead?: string;
}

class ApiRequestError extends Error {
  readonly status?: number;
  readonly code?: string;

  constructor(message: string, status?: number, code?: unknown) {
    super(message);
    this.name = 'ApiRequestError';
    this.status = status;
    this.code = code === undefined || code === null ? undefined : String(code);
  }
}

class LoginApiError extends Error {
  readonly code: string;

  constructor(code: string, message: string) {
    super(message);
    this.name = 'LoginApiError';
    this.code = code;
  }
}

const SESSION_EXPIRED_MESSAGE = '登录已过期，请重新登录';
const SESSION_EXPIRED_CODES = new Set(['0009', '0014', 'E0001', 'E0014']);
const SESSION_EXPIRED_ERROR = Symbol('session-expired');
const SESSION_STALE_ERROR = Symbol('session-stale');
let sessionExpiryHandled = false;
let sessionGeneration = 0;
let readCacheGeneration = 0;

type SessionExpiredError = Error & { [SESSION_EXPIRED_ERROR]?: true };
type SessionStaleError = Error & { [SESSION_STALE_ERROR]?: true };

interface SessionSnapshot {
  generation: number;
  sessionId: string | null;
  accessToken: string | null;
  refreshToken: string | null;
  userInfo: string | null;
}

interface RefreshTokenFlight extends SessionSnapshot {
  promise: Promise<string>;
}

let refreshTokenFlight: RefreshTokenFlight | null = null;
const SESSION_ID_KEY = 'auth-session-id';

const newSessionId = (): string => (
  typeof crypto !== 'undefined' && typeof crypto.randomUUID === 'function'
    ? crypto.randomUUID()
    : `${Date.now().toString(36)}-${Math.random().toString(36).slice(2)}`
);

const currentSessionId = (): string | null => {
  const refreshToken = localStorage.getItem('refresh-token');
  if (!refreshToken) return null;
  let sessionId = localStorage.getItem(SESSION_ID_KEY);
  if (!sessionId) {
    // One-time migration for sessions created by a pre-rotation client.
    sessionId = newSessionId();
    localStorage.setItem(SESSION_ID_KEY, sessionId);
  }
  return sessionId;
};

const createSessionExpiredError = (): SessionExpiredError => {
  const error = new Error(SESSION_EXPIRED_MESSAGE) as SessionExpiredError;
  error[SESSION_EXPIRED_ERROR] = true;
  return error;
};

const isSessionExpiredError = (error: unknown): error is SessionExpiredError => (
  error instanceof Error && Boolean((error as SessionExpiredError)[SESSION_EXPIRED_ERROR])
);

const createSessionStaleError = (): SessionStaleError => {
  const error = new Error('登录状态已更新，请重试') as SessionStaleError;
  error[SESSION_STALE_ERROR] = true;
  return error;
};

const isSessionStaleError = (error: unknown): error is SessionStaleError => (
  error instanceof Error && Boolean((error as SessionStaleError)[SESSION_STALE_ERROR])
);

const captureSession = (): SessionSnapshot => ({
  generation: sessionGeneration,
  sessionId: currentSessionId(),
  accessToken: localStorage.getItem('access-token'),
  refreshToken: localStorage.getItem('refresh-token'),
  userInfo: localStorage.getItem('userInfo'),
});

const isSameSession = (snapshot: SessionSnapshot): boolean => (
  sessionGeneration === snapshot.generation
  && currentSessionId() === snapshot.sessionId
);

const isSameSessionIdentity = (left: SessionSnapshot, right: SessionSnapshot): boolean => (
  left.generation === right.generation
  && left.sessionId === right.sessionId
  && left.refreshToken === right.refreshToken
);

const clearAuthenticatedRequestCache = () => {
  // In-flight reads from an earlier generation may finish, but cannot refill caches.
  readCacheGeneration += 1;
  const inflight = (globalThis as any).__inflightAuthReqs;
  const recent = (globalThis as any).__recentAuthCache;
  if (inflight instanceof Map) inflight.clear();
  if (recent instanceof Map) recent.clear();
};

const clearStoredSession = (clearFingerprint = false) => {
  sessionGeneration += 1;
  localStorage.removeItem('access-token');
  localStorage.removeItem('refresh-token');
  localStorage.removeItem(SESSION_ID_KEY);
  localStorage.removeItem('userInfo');
  if (clearFingerprint) localStorage.removeItem('client-fingerprint');
  clearAuthenticatedRequestCache();
};

const expireSession = () => {
  clearStoredSession();

  if (sessionExpiryHandled || typeof window === 'undefined') return;
  sessionExpiryHandled = true;

  const currentPath = window.location.pathname.replace(/\/$/, '') || '/';
  if (currentPath !== '/login' && currentPath !== '/web/login') {
    window.location.replace('/login');
  }
};

const refreshAccessTokenOnce = (session: SessionSnapshot): Promise<string> => {
  if (!isSameSession(session)) {
    return Promise.reject(createSessionStaleError());
  }

  const storedRefreshToken = localStorage.getItem('refresh-token');
  if (storedRefreshToken !== session.refreshToken) {
    const committedAccessToken = localStorage.getItem('access-token');
    if (storedRefreshToken && committedAccessToken) {
      return Promise.resolve(committedAccessToken);
    }
    return Promise.reject(createSessionExpiredError());
  }

  if (refreshTokenFlight && isSameSessionIdentity(refreshTokenFlight, session)) {
    return refreshTokenFlight.promise;
  }

  const promise = AuthService.refreshToken(session).finally(() => {
    if (refreshTokenFlight?.promise === promise) {
      refreshTokenFlight = null;
    }
  });
  refreshTokenFlight = { ...session, promise };
  return promise;
};

// 受保护接口已经明确返回 401 时，刷新失败代表当前页面无法继续认证。
// 主动刷新仍允许 403/5xx/网络异常保留会话；只有 401 恢复链路采用
// fail-closed，避免后台停留在旧权限状态或持续请求失效接口。
const recoverUnauthorizedSession = async (session: SessionSnapshot): Promise<string> => {
  try {
    return await refreshAccessTokenOnce(session);
  } catch (error) {
    if (isSessionStaleError(error) || isSessionExpiredError(error)) {
      if (isSessionExpiredError(error) && isSameSession(session)) {
        expireSession();
      }
      throw error;
    }

    const currentAccessToken = localStorage.getItem('access-token');
    if (
      currentAccessToken
      && currentAccessToken !== session.accessToken
      && isSameSession(session)
    ) {
      return currentAccessToken;
    }

    if (!isSameSession(session)) {
      throw createSessionStaleError();
    }
    expireSession();
    throw createSessionExpiredError();
  }
};

// 用户角色接口定义
export interface UserRole {
  roleId: number;
  name: string;
  description: string;
}

// 用户角色枚举
export enum UserRoleEnum {
  SUPER_ADMIN = 'SUPER_ADMIN',
  ADMIN = 'ADMIN',
  USER = 'USER',
  MANAGER = 'MANAGER'
}

const normalizeRoleCode = (roleName?: string): string => roleName?.trim().toUpperCase() || '';
const SUPER_ADMIN_ROLE_CODES = new Set(['SUPER_ADMIN', 'SUPERADMIN']);
const isSuperAdminRoleName = (roleName?: string): boolean => (
  SUPER_ADMIN_ROLE_CODES.has(normalizeRoleCode(roleName))
);
const isAdminRoleName = (roleName?: string): boolean => {
  const roleCode = normalizeRoleCode(roleName);
  return roleCode === 'ADMIN' || SUPER_ADMIN_ROLE_CODES.has(roleCode);
};

// 登录请求接口
export interface LoginRequest {
  phoneNumber: string;
  password: string;
  clientFingerprint: string;
}

export interface SmsLoginRequest {
  phoneNumber: string;
  verificationCode: string;
  clientFingerprint: string;
}

// 登录响应接口
export interface LoginResponse {
  userId: string;
  nickName: string;
  avatar: string;
  phoneNumber: string;
  gender: string;
  accessToken: string;
  refreshToken: string;
  tokenHead: string;
  roles?: UserRole[]; // 用户角色列表，可选字段
  permissions?: string[];
}

// 用户信息接口
export interface UserInfo {
  userId: string;
  nickName: string;
  avatar: string;
  phoneNumber: string;
  gender: string;
  roles?: UserRole[];
  permissions?: string[];
}

// 生成客户端指纹
export const generateClientFingerprint = (): string => {
  // 基于浏览器特征生成唯一指纹
  const canvas = document.createElement('canvas');
  const ctx = canvas.getContext('2d');
  ctx!.textBaseline = 'top';
  ctx!.font = '14px Arial';
  ctx!.fillText('Client fingerprint', 2, 2);
  
  const fingerprint = [
    navigator.userAgent,
    navigator.language,
    screen.width + 'x' + screen.height,
    new Date().getTimezoneOffset(),
    canvas.toDataURL(),
    navigator.hardwareConcurrency || 0,
    (navigator as any).deviceMemory || 0
  ].join('|');
  
  // 生成简单的hash
  let hash = 0;
  for (let i = 0; i < fingerprint.length; i++) {
    const char = fingerprint.charCodeAt(i);
    hash = ((hash << 5) - hash) + char;
    hash = hash & hash; // Convert to 32bit integer
  }
  
  return Math.abs(hash).toString(16);
};

// 获取存储的客户端指纹
export const getClientFingerprint = (): string => {
  let fingerprint = localStorage.getItem('client-fingerprint');
  if (!fingerprint) {
    fingerprint = generateClientFingerprint();
    localStorage.setItem('client-fingerprint', fingerprint);
  }
  return fingerprint;
};

// 通用请求函数
const customApiRequest = async <T>(
  url: string,
  options: RequestInit = {},
  uuid?: string
): Promise<ApiResponse<T>> => {
  const baseHeaders: Record<string, string> = {
    'Content-Type': 'application/json',
    'Accept': 'application/json'
  };
  const storedAccess = localStorage.getItem('access-token');
  const storedRefresh = localStorage.getItem('refresh-token');
  const fp = getClientFingerprint();
  // 默认携带指纹
  baseHeaders['client-fingerprint'] = fp;
  // 若未显式提供Authorization，则自动附加（优先使用access-token）
  if (!(options.headers && (options.headers as any)['Authorization'])) {
    const bearer = storedAccess || storedRefresh;
    if (bearer) baseHeaders['Authorization'] = `Bearer ${bearer}`;
  }
  
  // 如果传入了uuid，添加Idempotency-Token头
  if (uuid) {
    baseHeaders['Idempotency-Token'] = uuid;
  }
  
  const headers = new Headers(baseHeaders);
  new Headers(options.headers).forEach((value, key) => headers.set(key, value));

  const response = await fetch(`${BASE_URL}${url}`, {
    ...options,
    headers,
  });

  if (!response.ok) {
    throw new ApiRequestError(`HTTP error! status: ${response.status}`, response.status);
  }

  // 使用自定义JSON解析处理大整数精度问题
  const text = await response.text();
  
  // 预处理JSON字符串，将大整数ID字段用引号包围
  const preprocessedText = text.replace(
    /"(\w*[Ii]d\w*)"\s*:\s*(\d{16,})/g,
    '"$1":"$2"'
  );
  
  const result = JSON.parse(preprocessedText, (key, value) => {
    // 检测ID字段，确保为字符串格式
    if (typeof value === 'string' && 
        (key.includes('Id') || key === 'userId' || 
         key.includes('id') || key.includes('ID')) && 
        /^\d+$/.test(value)) {
      return value;
    }
    return value;
  });
  
  // 检查业务状态码（兼容 200 / "200" / "0000"）
  const bizCode: any = (result as any)?.code;
  const ok = bizCode === 200 || bizCode === '200' || bizCode === '0000';
  if (!ok) {
    const msgText = (result as any)?.msg ?? (result as any)?.info ?? '请求失败';
    throw new ApiRequestError(msgText, response.status, bizCode);
  }
  // 归一化返回结构，确保 msg/info 一致
  const normalized: ApiResponse<T> = {
    code: bizCode,
    msg: (result as any)?.msg ?? (result as any)?.info ?? 'OK',
    data: (result as any)?.data as T,
  };
  return normalized;
};

// 需要认证的请求函数（带401自动刷新Token机制）
// 默认携带幂等令牌，确保请求的幂等性
/**
 * 需要认证的通用请求函数，自动处理401刷新token。
 * 响应兼容两种格式：
 * - 标准业务响应：`{ code:number, msg:string, data:T }`
 * - 直出数据：直接返回数据对象或数组（无`code`），函数会包装为标准结构。
 * 同时处理大整数ID的字符串化，避免精度丢失。
 * @param url 相对路径，如 `/api/admin/order/page`
 * @param options 请求配置，自动注入认证头与幂等令牌
 * @param retryCount 刷新token后的重试计数
 * @param uuid 可选幂等令牌，不传则自动生成
 * @returns 标准业务响应结构 `ApiResponse<T>`
 */
export type AuthenticatedRequestOptions = RequestInit & { timeoutMs?: number };

export const authenticatedRequest = async <T>(
  url: string,
  options: AuthenticatedRequestOptions = {},
  retryCount = 0,
  uuid?: string
): Promise<ApiResponse<T>> => {
  const accessToken = localStorage.getItem('access-token');
  const requestSession = captureSession();
  const clientFingerprint = getClientFingerprint();
  
  if (!accessToken) {
    expireSession();
    throw createSessionExpiredError();
  }

  const method = (options.method || 'GET').toString().toUpperCase();
  const invalidatesReadCache = method !== 'GET';
  if (invalidatesReadCache) clearAuthenticatedRequestCache();
  try {
    const requestReadGeneration = readCacheGeneration;
    const bodyStr = typeof options.body === 'string' ? options.body : (options.body ? JSON.stringify(options.body) : '');
    const dedupKey = `${method}:${url}:${bodyStr}`;
    const now = Date.now();
    const dedupWindow = 1200;
    const shouldDeduplicate = method === 'GET' && retryCount === 0;
    const inflight = (globalThis as any).__inflightAuthReqs || new Map<string, {
      promise: Promise<ApiResponse<any>>;
      ts: number;
      session: SessionSnapshot;
      cacheGeneration: number;
    }>();
    (globalThis as any).__inflightAuthReqs = inflight;
    const recent = (globalThis as any).__recentAuthCache || new Map<string, {
      res: ApiResponse<any>;
      ts: number;
      session: SessionSnapshot;
      cacheGeneration: number;
    }>();
    (globalThis as any).__recentAuthCache = recent;
    if (shouldDeduplicate) {
      if (recent.has(dedupKey)) {
        const entry = recent.get(dedupKey)!;
        if (
          now - entry.ts < dedupWindow
          && entry.cacheGeneration === requestReadGeneration
          && isSameSessionIdentity(entry.session, requestSession)
          && isSameSession(entry.session)
        ) {
          return entry.res as ApiResponse<T>;
        } else {
          recent.delete(dedupKey);
        }
      }
      if (inflight.has(dedupKey)) {
        const entry = inflight.get(dedupKey)!;
        if (
          now - entry.ts < dedupWindow
          && entry.cacheGeneration === requestReadGeneration
          && isSameSessionIdentity(entry.session, requestSession)
          && isSameSession(entry.session)
        ) {
          return entry.promise as Promise<ApiResponse<T>>;
        } else {
          inflight.delete(dedupKey);
        }
      }
    }
    // 如果没有传入uuid，自动生成一个幂等令牌
    const idempotencyToken = uuid || uuidv4();
    
    // 构建请求头，如果是FormData则不设置Content-Type
    const baseHeaders: Record<string, string> = {
      'Authorization': `Bearer ${accessToken}`,
      'client-fingerprint': clientFingerprint,
      'Idempotency-Token': idempotencyToken,
      'Accept': 'application/json',
    };
    
    // 只有在不是FormData时才设置Content-Type为application/json
    if (!(options.body instanceof FormData)) {
      baseHeaders['Content-Type'] = 'application/json';
    }
    
    // 认证头由请求层统一管理，确保 Token 刷新后重试使用最新值。
    const headers = new Headers(options.headers);
    headers.set('Authorization', baseHeaders.Authorization);
    headers.set('client-fingerprint', baseHeaders['client-fingerprint']);
    headers.set('Idempotency-Token', baseHeaders['Idempotency-Token']);
    headers.set('Accept', baseHeaders.Accept);
    if (options.body instanceof FormData) {
      headers.delete('Content-Type');
    } else if (!headers.has('Content-Type')) {
      headers.set('Content-Type', baseHeaders['Content-Type']);
    }
    
    const { timeoutMs = API_CONFIG.TIMEOUT, ...fetchOptions } = options;
    if (!Number.isFinite(timeoutMs) || timeoutMs <= 0 || timeoutMs > 300000) {
      throw new Error('请求超时时间必须介于 1 和 300000 毫秒之间');
    }
    const controller = new AbortController();
    const timeout = setTimeout(() => controller.abort(), timeoutMs);
    const requestPromise = (async () => {
      const response = await fetch(`${BASE_URL}${url}`, {
        ...fetchOptions,
        headers,
        signal: controller.signal,
      });

    // 401 首次刷新后重试；刷新失败或重试仍为 401 则统一退回登录页。
    if (response.status === 401) {
      if (!isSameSession(requestSession)) {
        throw createSessionStaleError();
      }

      if (localStorage.getItem('access-token') !== accessToken) {
        if (retryCount === 0) {
          return authenticatedRequest(url, options, retryCount + 1, uuid);
        }
        throw createSessionStaleError();
      }

      if (retryCount === 0) {
        console.log('收到401响应，尝试刷新Token');
        await recoverUnauthorizedSession(requestSession);
        if (!isSameSession(requestSession)) {
          throw createSessionStaleError();
        }
        console.log('Token刷新成功，重新发送请求');
        return authenticatedRequest(url, options, retryCount + 1, uuid);
      }

      if (!isSameSession(requestSession)) {
        throw createSessionStaleError();
      }
      expireSession();
      throw createSessionExpiredError();
    }

    if (response.status === 403) {
      throw new Error('权限不足');
    }

    if (!response.ok) {
      throw new Error(`HTTP error! status: ${response.status}`);
    }

    // 使用相同的JSON预处理逻辑
    const text = await response.text();
    const preprocessedText = text.replace(
      /"(\w*[Ii]d\w*)"\s*:\s*(\d{16,})/g,
      '"$1":"$2"'
    );
    
    const parsed = JSON.parse(preprocessedText, (key, value) => {
      if (typeof value === 'string' && 
          (key.includes('Id') || key === 'userId' || 
           key.includes('id') || key.includes('ID')) && 
          /^\d+$/.test(value)) {
        return value;
      }
      return value;
    });
    
    // 兼容响应结构：
    // - 若包含 code 字段，按统一规范检查业务状态码（支持 code: 200 或 "0000"）
    // - 若不包含 code，视为直出数据，包裹为统一返回结构
    if (parsed && typeof parsed === 'object' && 'code' in parsed) {
      const parsedAny: any = parsed;
      const bizCode = parsedAny.code;
      const ok = bizCode === 200 || bizCode === '200' || bizCode === '0000';
      const msgText = parsedAny.msg ?? parsedAny.info ?? 'OK';
      if (!ok) {
        if (SESSION_EXPIRED_CODES.has(String(bizCode))) {
          if (!isSameSession(requestSession)) {
            throw createSessionStaleError();
          }
          if (localStorage.getItem('access-token') !== accessToken) {
            if (retryCount === 0) {
              return authenticatedRequest(url, options, retryCount + 1, uuid);
            }
            throw createSessionStaleError();
          }
          expireSession();
          throw createSessionExpiredError();
        }
        throw new Error(msgText || `请求失败 (错误代码: ${bizCode})`);
      }
      const resObj: ApiResponse<T> = {
        code: bizCode,
        msg: msgText,
        data: parsedAny.data as T,
      };
      if (!isSameSession(requestSession)) {
        throw createSessionStaleError();
      }
      if (shouldDeduplicate && requestReadGeneration === readCacheGeneration) {
        recent.set(dedupKey, {
          res: resObj as ApiResponse<any>,
          ts: Date.now(),
          session: requestSession,
          cacheGeneration: requestReadGeneration,
        });
      }
      return resObj;
    }

    // 直出数据（无 code 字段）
    const wrapped: ApiResponse<T> = {
      code: 200,
      msg: 'OK',
      data: parsed as T,
    };
    if (!isSameSession(requestSession)) {
      throw createSessionStaleError();
    }
    if (shouldDeduplicate && requestReadGeneration === readCacheGeneration) {
      recent.set(dedupKey, {
        res: wrapped as ApiResponse<any>,
        ts: Date.now(),
        session: requestSession,
        cacheGeneration: requestReadGeneration,
      });
    }
    return wrapped;
    })();
    if (shouldDeduplicate && requestReadGeneration === readCacheGeneration) {
      inflight.set(dedupKey, {
        promise: requestPromise,
        ts: now,
        session: requestSession,
        cacheGeneration: requestReadGeneration,
      });
    }
    try {
      const res = await requestPromise;
      if (shouldDeduplicate && inflight.get(dedupKey)?.promise === requestPromise) {
        inflight.delete(dedupKey);
      }
      return res as ApiResponse<T>;
    } catch (e) {
      if (shouldDeduplicate && inflight.get(dedupKey)?.promise === requestPromise) {
        inflight.delete(dedupKey);
      }
      throw e;
    } finally {
      clearTimeout(timeout);
    }
  } catch (error) {
    console.error('Authenticated request failed');
    // 提供更具体的错误信息
    if (isSessionExpiredError(error) || isSessionStaleError(error)) {
      throw error;
    }
    if (error instanceof Error) {
      throw new Error(error.message);
    } else {
      throw new Error('未知网络错误');
    }
  } finally {
    // Also invalidate reads issued while the mutation was pending, even on an
    // uncertain failure: the server may already have committed the write.
    if (invalidatesReadCache) clearAuthenticatedRequestCache();
  }
};

// 认证服务类
export class AuthService {
  // 内部API请求方法
  static async apiRequest<T>(url: string, options: RequestInit = {}): Promise<ApiResponse<T>> {
    // 为所有API请求自动生成幂等令牌
    const idempotencyToken = uuidv4();
    return customApiRequest<T>(url, options, idempotencyToken);
  }

  // 专门用于登录的API请求方法
  static async loginApiRequest<T>(url: string, options: RequestInit = {}): Promise<LoginApiResponse<T>> {
    const headers = {
      'Content-Type': 'application/json',
      'Idempotency-Token': uuidv4(),
      ...options.headers,
    };

    const response = await fetch(`${BASE_URL}${url}`, {
      ...options,
      headers,
    }).catch(() => {
      throw new Error('网络连接失败，请检查网络后重试');
    });

    if (!response.ok) {
      throw new Error(response.status === 429
        ? '操作过于频繁，请稍后重试'
        : response.status >= 500
          ? '登录服务暂时不可用，请稍后重试'
          : '登录请求失败，请检查输入后重试');
    }

    const text = await response.text();
    const result = JSON.parse(text, (key, value) => {
      if (typeof value === 'number' && value > Number.MAX_SAFE_INTEGER) {
        return value.toString();
      }
      return value;
    }) as LoginApiResponse<T>;

    // 检查登录接口的业务状态码
    if (result.code !== '0000') {
      throw new LoginApiError(result.code, result.info || '请求失败');
    }

    return result;
  }

  // 两种登录方式共用后台身份校验及会话保存。
  static async login(credentials: LoginRequest): Promise<LoginResponse> {
    return this.authenticate(API_CONFIG.ENDPOINTS.LOGIN, credentials);
  }

  static async smsLogin(credentials: SmsLoginRequest): Promise<LoginResponse> {
    return this.authenticate('/api/v1/auth/login/verification-code', credentials);
  }

  static async sendLoginCode(phoneNumber: string): Promise<void> {
    await this.loginApiRequest<null>(`/api/v1/auth/login/send?phoneNumber=${encodeURIComponent(phoneNumber.trim())}`, {
      method: 'POST',
    });
  }

  private static async authenticate(url: string, credentials: LoginRequest | SmsLoginRequest): Promise<LoginResponse> {
    try {
      // 添加客户端指纹
      const loginData = {
        ...credentials,
        clientFingerprint: getClientFingerprint(),
        loginType: 2,
      };

      const response = await this.loginApiRequest<LoginResponse>(url, {
        method: 'POST',
        body: JSON.stringify(loginData),
      });

      // 存储token和用户信息
      if (response.data) {
        const { accessToken, refreshToken, ...userInfo } = response.data;
        
        // 检查管理员权限
        const hasAdminRole = userInfo.roles?.some(role => isAdminRoleName(role.name));
        
        if (!hasAdminRole) {
          throw new Error('您没有管理员权限，无法访问后台管理系统');
        }

        clearStoredSession();
        localStorage.setItem('access-token', accessToken);
        if (refreshToken) {
          localStorage.setItem('refresh-token', refreshToken);
          localStorage.setItem(SESSION_ID_KEY, newSessionId());
        }
        
        // 存储用户信息
        localStorage.setItem('userInfo', JSON.stringify(userInfo));
        sessionExpiryHandled = false;
        
        return response.data;
      }

      throw new Error('登录响应数据格式错误');
    } catch (error) {
      console.error('Login failed');
      throw error;
    }
  }

  // 登出
  static async logout(): Promise<void> {
    const logoutSession = captureSession();
    try {
      await authenticatedRequest(API_CONFIG.ENDPOINTS.LOGOUT, {
        method: 'POST',
        body: JSON.stringify({ refreshToken: logoutSession.refreshToken }),
      });
    } catch {
      console.error('Logout failed');
    } finally {
      // 只清理由本次登出发起的会话，避免旧请求误清新登录会话。
      if (isSameSession(logoutSession)) {
        clearStoredSession(true);
      }
    }
  }

  // 获取当前用户信息
  static async getCurrentUser(): Promise<UserInfo> {
    try {
      const response = await authenticatedRequest<UserInfo>(API_CONFIG.ENDPOINTS.USER_INFO);
      return response.data;
    } catch (error) {
      console.error('Get user info failed');
      throw error;
    }
  }

  // 刷新token
  static async refreshToken(session = captureSession()): Promise<string> {
    if (!isSameSession(session)) {
      throw createSessionStaleError();
    }

    const refreshToken = session.refreshToken;
    if (!refreshToken) {
      throw createSessionExpiredError();
    }

    try {
      const response = await customApiRequest<RefreshTokenResponse>(API_CONFIG.ENDPOINTS.REFRESH_TOKEN, {
        method: 'POST',
        cache: 'no-store',
        headers: {
          'Authorization': `Bearer ${refreshToken}`,
          'client-fingerprint': getClientFingerprint(),
        },
      });

      if (
        !response.data?.accessToken
        || !response.data?.refreshToken
      ) {
        throw createSessionExpiredError();
      }

      if (!isSameSession(session)) {
        throw createSessionStaleError();
      }

      // Refresh-token rotation stays in the same logical session. Keeping the
      // generation/sessionId stable prevents sibling requests from becoming
      // stale solely because the refresh credential changed.
      localStorage.setItem('access-token', response.data.accessToken);
      localStorage.setItem('refresh-token', response.data.refreshToken);
      return response.data.accessToken;
    } catch (error) {
      console.error('Refresh token failed');
      if (isSessionExpiredError(error) || isSessionStaleError(error)) throw error;
      if (!isSameSession(session)) throw createSessionStaleError();
      if (
        error instanceof ApiRequestError
        && (
          error.status === 401
          || (
            error.status !== undefined
            && error.status >= 200
            && error.status < 300
            && SESSION_EXPIRED_CODES.has(String(error.code))
          )
        )
      ) {
        throw createSessionExpiredError();
      }
      throw error;
    }
  }

  // 检查是否已登录
  static isAuthenticated(): boolean {
    const token = localStorage.getItem('access-token');
    return !!token;
  }

  // 获取存储的用户信息
  static getStoredUserInfo(): UserInfo | null {
    try {
      const userInfo = localStorage.getItem('userInfo');
      return userInfo ? JSON.parse(userInfo) : null;
    } catch {
      console.error('Parse user info failed');
      return null;
    }
  }

  // 检查用户是否为管理员
  static isAdmin(): boolean {
    const userInfo = this.getStoredUserInfo();
    return userInfo?.roles?.some(role => isAdminRoleName(role.name)) || false;
  }

  // 路由守卫发现本地认证数据不完整或管理员角色失效时统一结束会话。
  static invalidateSession(): void {
    expireSession();
  }

  // 账号管理中的 superadmin 角色对应后台超级管理员。
  static isSuperAdmin(): boolean {
    const userInfo = this.getStoredUserInfo();
    return userInfo?.roles?.some(role => isSuperAdminRoleName(role.name)) || false;
  }

  // 获取token
  static getToken(): string | null {
    return localStorage.getItem('access-token');
  }
}
