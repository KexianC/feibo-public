import { v4 as uuidv4 } from 'uuid';
import { getClientFingerprint, recoverUnauthorizedSession } from '@/services/authApi';
import {
  AuthSessionExpiredError,
  authSession,
  isSessionExpiredCode,
} from '@/services/authSession';
import { requestRaw, type RequestOptions } from '@/services/http/client';

interface SessionErrorEnvelope {
  code?: unknown;
  info?: unknown;
  message?: unknown;
}

export interface AuthenticatedRawRequestConfig {
  retryCount?: number;
  idempotent?: boolean;
  idempotencyKey?: string;
  inspectJsonSessionCode?: boolean;
}

function shouldUseIdempotency(method: string, idempotent?: boolean): boolean {
  if (typeof idempotent === 'boolean') {
    return idempotent;
  }
  return !['GET', 'HEAD', 'OPTIONS'].includes(method);
}

async function readSessionError(response: Response): Promise<SessionErrorEnvelope | null> {
  const contentType = response.headers.get('content-type')?.toLowerCase() || '';
  if (!contentType.includes('json')) {
    return null;
  }

  try {
    const payload = await response.clone().json() as SessionErrorEnvelope;
    return isSessionExpiredCode(payload?.code) ? payload : null;
  } catch {
    return null;
  }
}

/**
 * Sends an authenticated request whose successful response body must remain
 * untouched for the caller (SSE, blobs, or plain text).
 *
 * A 401 may be retried once while only response headers have been observed.
 * Once this function returns, the caller owns the body and no automatic replay
 * can occur after stream consumption has started.
 */
export async function authenticatedRawRequest(
  url: string,
  options: RequestOptions = {},
  config: AuthenticatedRawRequestConfig = {},
): Promise<Response> {
  const {
    retryCount = 0,
    idempotent,
    idempotencyKey,
    inspectJsonSessionCode = true,
  } = config;
  const method = (options.method || 'GET').toUpperCase();
  const requestSession = authSession.capture();
  const accessToken = requestSession.accessToken;

  if (!accessToken) {
    authSession.expire(requestSession);
    throw new AuthSessionExpiredError();
  }

  const effectiveIdempotencyKey = shouldUseIdempotency(method, idempotent)
    ? (idempotencyKey || uuidv4())
    : undefined;
  const headers = new Headers(options.headers);

  // Security-sensitive headers are written last so a stale caller value cannot
  // override the current authenticated session.
  headers.set('Authorization', `Bearer ${accessToken}`);
  headers.set('client-fingerprint', getClientFingerprint());
  if (effectiveIdempotencyKey) {
    headers.set('Idempotency-Token', effectiveIdempotencyKey);
  }

  const response = await requestRaw(url, {
    ...options,
    headers,
  });
  authSession.assertCurrent(requestSession);

  if (response.status === 401) {
    if (localStorage.getItem('access-token') !== accessToken && retryCount === 0) {
      return authenticatedRawRequest(url, options, {
        ...config,
        retryCount: retryCount + 1,
        idempotencyKey: effectiveIdempotencyKey,
      });
    }

    if (retryCount === 0) {
      await recoverUnauthorizedSession(requestSession);
      authSession.assertCurrent(requestSession);
      return authenticatedRawRequest(url, options, {
        ...config,
        retryCount: retryCount + 1,
        idempotencyKey: effectiveIdempotencyKey,
      });
    }

    authSession.expire(requestSession);
    throw new AuthSessionExpiredError();
  }

  // HTTP authorization semantics take precedence. In particular, a 403 or
  // 5xx body must never be reinterpreted as proof that the login expired.
  if (response.ok && inspectJsonSessionCode) {
    const sessionError = await readSessionError(response);
    authSession.assertCurrent(requestSession);
    if (sessionError) {
      if (localStorage.getItem('access-token') !== accessToken && retryCount === 0) {
        return authenticatedRawRequest(url, options, {
          ...config,
          retryCount: retryCount + 1,
          idempotencyKey: effectiveIdempotencyKey,
        });
      }

      authSession.expire(requestSession);
      const message = typeof sessionError.info === 'string'
        ? sessionError.info
        : typeof sessionError.message === 'string'
          ? sessionError.message
          : undefined;
      throw new AuthSessionExpiredError(message);
    }
  }

  return response;
}
