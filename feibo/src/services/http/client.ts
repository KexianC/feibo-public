import { REQUEST_CONFIG } from '@/config/api';
import { AppRequestError } from '@/services/http/errors';
import { parseApiJson } from '@/services/http/parsers';

export interface RequestResult<T> {
  response: Response;
  data: T;
}

const inFlightRequests = new Map<string, Promise<Response>>();
const requestControllers = new Map<string, AbortController>();

export interface RequestOptions extends RequestInit {
  timeoutMs?: number;
  dedupeKey?: string;
  reuseInflight?: boolean;
  cancelKey?: string;
}

export async function requestRaw(url: string, options: RequestOptions = {}) {
  const {
    timeoutMs = REQUEST_CONFIG.TIMEOUT,
    dedupeKey,
    reuseInflight = false,
    cancelKey,
    signal,
    ...fetchOptions
  } = options;

  if (dedupeKey && reuseInflight) {
    const existingRequest = inFlightRequests.get(dedupeKey);
    if (existingRequest) {
      return existingRequest;
    }
  }

  let externalAbortCleanup: (() => void) | undefined;
  const controller = new AbortController();

  if (cancelKey) {
    const previousController = requestControllers.get(cancelKey);
    if (previousController) {
      previousController.abort('cancelled-by-new-request');
    }
    requestControllers.set(cancelKey, controller);
  }

  if (signal) {
    if (signal.aborted) {
      controller.abort(signal.reason);
    } else {
      const forwardAbort = () => controller.abort(signal.reason);
      signal.addEventListener('abort', forwardAbort, { once: true });
      externalAbortCleanup = () => signal.removeEventListener('abort', forwardAbort);
    }
  }

  const timeoutId = window.setTimeout(() => controller.abort('request-timeout'), timeoutMs);

  const requestPromise = (async () => {
    try {
      return await fetch(url, {
        ...fetchOptions,
        signal: controller.signal,
      });
    } catch (error) {
      if (error instanceof DOMException && error.name === 'AbortError') {
        // Preserve an explicit caller abort. Streaming callers use this to
        // distinguish the user's stop action from a transport timeout.
        if (signal?.aborted) {
          throw error;
        }
        const reason = controller.signal.reason;
        if (reason === 'cancelled-by-new-request') {
          throw new AppRequestError('请求已取消');
        }
        throw new AppRequestError('请求超时');
      }

      throw error;
    } finally {
      window.clearTimeout(timeoutId);
      externalAbortCleanup?.();

      if (dedupeKey) {
        inFlightRequests.delete(dedupeKey);
      }

      if (cancelKey && requestControllers.get(cancelKey) === controller) {
        requestControllers.delete(cancelKey);
      }
    }
  })();

  if (dedupeKey) {
    inFlightRequests.set(dedupeKey, requestPromise);
  }

  return requestPromise;
}

export async function requestJson<T>(url: string, options: RequestOptions = {}): Promise<RequestResult<T>> {
  const response = await requestRaw(url, options);
  const text = await response.text();
  const data = text ? parseApiJson<T>(text) : (undefined as T);

  return {
    response,
    data,
  };
}
