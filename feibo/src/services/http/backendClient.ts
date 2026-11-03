import { v4 as uuidv4 } from 'uuid';
import { API_CONFIG } from '@/config/api';
import { AppRequestError } from '@/services/http/errors';
import { requestJson, type RequestOptions } from '@/services/http/client';
import { scopeRequestOptionsToSession } from '@/services/http/sessionRequestScope';
import { getClientFingerprint, recoverUnauthorizedSession } from '@/services/authApi';
import {
  AuthSessionExpiredError,
  authSession,
  isSessionExpiredCode,
} from '@/services/authSession';

export interface BackendApiResponse<T> {
  code: string;
  info: string;
  data: T;
}

interface BackendWireApiResponse<T> {
  code: string | number;
  info?: string;
  msg?: string;
  data: T;
}

interface BackendRequestConfig {
  auth?: boolean;
  retryCount?: number;
  idempotent?: boolean;
  idempotencyKey?: string;
}

function shouldUseIdempotency(method: string, idempotent?: boolean) {
  if (typeof idempotent === 'boolean') {
    return idempotent;
  }

  return !['GET', 'HEAD', 'OPTIONS'].includes(method);
}

export async function backendRequest<T>(
  url: string,
  options: RequestOptions = {},
  config: BackendRequestConfig = {},
): Promise<BackendApiResponse<T>> {
  const { auth = true, retryCount = 0, idempotent, idempotencyKey } = config;
  const method = (options.method || 'GET').toUpperCase();
  const headers = new Headers(options.headers);
  const requestSession = authSession.capture();
  const accessToken = requestSession.accessToken;
  const effectiveIdempotencyKey = shouldUseIdempotency(method, idempotent)
    ? (idempotencyKey || uuidv4())
    : undefined;

  headers.set('client-fingerprint', getClientFingerprint());

  if (auth) {
    if (!accessToken) {
      authSession.expire(requestSession);
      throw new AuthSessionExpiredError();
    }

    // 认证头由请求层最后写入，调用者携带的旧 token 无法覆盖。
    headers.set('Authorization', `Bearer ${accessToken}`);
  }

  if (!(options.body instanceof FormData) && !headers.has('Content-Type')) {
    headers.set('Content-Type', 'application/json');
  }

  if (effectiveIdempotencyKey) {
    headers.set('Idempotency-Token', effectiveIdempotencyKey);
  }

  const requestOptions = auth
    ? scopeRequestOptionsToSession(options, requestSession)
    : options;
  const { response, data } = await requestJson<BackendWireApiResponse<T>>(`${API_CONFIG.API_BASE_URL}${url}`, {
    ...requestOptions,
    headers,
  });

  if (auth) {
    authSession.assertCurrent(requestSession);
  }

  if (response.status === 401 && auth) {
    if (localStorage.getItem('access-token') !== accessToken && retryCount === 0) {
      return backendRequest<T>(url, options, {
        ...config,
        retryCount: retryCount + 1,
        idempotencyKey: effectiveIdempotencyKey,
      });
    }

    if (retryCount === 0) {
      await recoverUnauthorizedSession(requestSession);
      authSession.assertCurrent(requestSession);
      return backendRequest<T>(url, options, {
        ...config,
        retryCount: retryCount + 1,
        idempotencyKey: effectiveIdempotencyKey,
      });
    }

    authSession.expire(requestSession);
    throw new AuthSessionExpiredError();
  }

  if (!response.ok) {
    throw new AppRequestError(data.info || data.msg || `HTTP error! status: ${response.status}`, {
      status: response.status,
      code: data.code,
    });
  }

  const responseCode = String(data.code);
  if (responseCode !== '0000' && responseCode !== '200') {
    if (auth && isSessionExpiredCode(data.code)) {
      if (localStorage.getItem('access-token') !== accessToken && retryCount === 0) {
        return backendRequest<T>(url, options, {
          ...config,
          retryCount: retryCount + 1,
          idempotencyKey: effectiveIdempotencyKey,
        });
      }

      authSession.expire(requestSession);
      throw new AuthSessionExpiredError(data.info || data.msg);
    }

    throw new AppRequestError(data.info || data.msg || '请求失败', {
      status: response.status,
      code: data.code,
    });
  }

  return {
    code: responseCode,
    info: data.info || data.msg || '',
    data: data.data,
  };
}

export async function backendDataRequest<T>(
  url: string,
  options: RequestOptions = {},
  config: BackendRequestConfig = {},
) {
  const response = await backendRequest<T>(url, options, config);
  return response.data;
}
