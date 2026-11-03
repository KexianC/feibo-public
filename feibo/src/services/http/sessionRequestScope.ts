import type { AuthSessionSnapshot } from '@/services/authSession';
import type { RequestOptions } from '@/services/http/client';

/**
 * Bind request de-duplication and cancellation keys to one logical login.
 * Browser tabs do not share the in-memory request maps, so generation is a
 * safe fallback for legacy sessions that do not yet have a persisted ID.
 */
export function scopeRequestOptionsToSession(
  options: RequestOptions,
  snapshot: AuthSessionSnapshot,
): RequestOptions {
  if (!options.dedupeKey && !options.cancelKey) {
    return options;
  }

  const sessionScope = `${snapshot.generation}:${snapshot.sessionId || 'legacy'}`;

  return {
    ...options,
    dedupeKey: options.dedupeKey
      ? `auth:${sessionScope}:${options.dedupeKey}`
      : undefined,
    cancelKey: options.cancelKey
      ? `auth:${sessionScope}:${options.cancelKey}`
      : undefined,
  };
}
