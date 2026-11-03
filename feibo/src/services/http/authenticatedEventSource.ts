import { EventSourcePolyfill } from 'event-source-polyfill';
import { getClientFingerprint, recoverUnauthorizedSession } from '@/services/authApi';
import {
  AuthSessionExpiredError,
  authSession,
  type AuthSessionSnapshot,
} from '@/services/authSession';

interface EventSourceErrorEvent extends Event {
  status?: number;
  statusText?: string;
  error?: unknown;
}

export interface AuthenticatedEventSourceOptions {
  headers?: HeadersInit;
  heartbeatTimeout?: number;
  withCredentials?: boolean;
}

export interface AuthenticatedEventSourceHandlers {
  onMessage: (event: MessageEvent<string>) => void;
  onOpen?: (event: Event) => void;
  onError: (error: unknown) => void;
}

export interface AuthenticatedEventSourceConnection {
  close(): void;
}

/**
 * Opens an authenticated EventSource and performs at most one 401 refresh
 * retry before the stream is opened. An error after onopen/onmessage is never
 * replayed because the server may already have emitted or acted on data.
 */
export function openAuthenticatedEventSource(
  url: string,
  options: AuthenticatedEventSourceOptions,
  handlers: AuthenticatedEventSourceHandlers,
): AuthenticatedEventSourceConnection {
  const logicalSession = authSession.capture();
  let source: EventSourcePolyfill | null = null;
  let closed = false;
  let retryCount = 0;

  const fail = (error: unknown) => {
    if (!closed) {
      handlers.onError(error);
    }
  };

  const retryUnauthorized = async (attemptSession: AuthSessionSnapshot) => {
    try {
      authSession.assertCurrent(attemptSession);
      if (localStorage.getItem('access-token') === attemptSession.accessToken) {
        await recoverUnauthorizedSession(attemptSession);
      }
      authSession.assertCurrent(logicalSession);
      connect();
    } catch (error) {
      fail(error);
    }
  };

  const connect = () => {
    if (closed) return;
    authSession.assertCurrent(logicalSession);

    const attemptSession = authSession.capture();
    if (!attemptSession.accessToken) {
      authSession.expire(attemptSession);
      fail(new AuthSessionExpiredError());
      return;
    }

    const headers = new Headers(options.headers);
    // Authentication state is authoritative over caller-supplied headers.
    headers.set('Authorization', `Bearer ${attemptSession.accessToken}`);
    headers.set('client-fingerprint', getClientFingerprint());

    let bodyStarted = false;
    const candidate = new EventSourcePolyfill(url, {
      ...options,
      headers: Object.fromEntries(headers.entries()),
    });
    source = candidate;

    candidate.onopen = (event) => {
      if (source !== candidate || closed) return;
      try {
        authSession.assertCurrent(logicalSession);
      } catch (error) {
        candidate.close();
        fail(error);
        return;
      }
      bodyStarted = true;
      handlers.onOpen?.(event);
    };

    candidate.onmessage = (event) => {
      if (source !== candidate || closed) return;
      try {
        authSession.assertCurrent(logicalSession);
      } catch (error) {
        candidate.close();
        fail(error);
        return;
      }
      bodyStarted = true;
      handlers.onMessage(event as MessageEvent<string>);
    };

    candidate.onerror = (event: EventSourceErrorEvent) => {
      if (source !== candidate || closed) return;
      candidate.close();

      try {
        authSession.assertCurrent(logicalSession);
      } catch (error) {
        fail(error);
        return;
      }

      const status = Number(event?.status || 0);
      if (status === 401 && !bodyStarted && retryCount === 0) {
        retryCount += 1;
        void retryUnauthorized(attemptSession);
        return;
      }

      if (status === 401 && !bodyStarted) {
        authSession.expire(attemptSession);
        fail(new AuthSessionExpiredError());
        return;
      }

      // Network failures, 403, 5xx, and errors after stream consumption do
      // not prove session expiry and must not clear or replay the session.
      fail(event);
    };
  };

  try {
    if (!logicalSession.accessToken) {
      authSession.expire(logicalSession);
      fail(new AuthSessionExpiredError());
    } else {
      connect();
    }
  } catch (error) {
    fail(error);
  }

  return {
    close: () => {
      closed = true;
      source?.close();
      source = null;
    },
  };
}
