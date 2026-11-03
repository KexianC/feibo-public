export const SESSION_EXPIRED_MESSAGE = '登录已过期，请重新登录';

export const SESSION_EXPIRED_CODES = new Set([
  '0009', // access token 过期
  '0014', // refresh token 已注销
  'E0001', // 当前未登录
  'E0014', // 未授权
]);

const ACCESS_TOKEN_KEY = 'access-token';
const REFRESH_TOKEN_KEY = 'refresh-token';
const SESSION_ID_KEY = 'auth-session-id';
const SESSION_PROFILE_KEYS = ['user-info', 'user-auth'] as const;

export interface SessionStorageLike {
  getItem(key: string): string | null;
  setItem(key: string, value: string): void;
  removeItem(key: string): void;
}

export interface AuthSessionSnapshot {
  generation: number;
  sessionId: string | null;
  accessToken: string | null;
  refreshToken: string | null;
}

export interface RotatedTokenPair {
  accessToken: string;
  refreshToken: string;
}

interface RefreshFlight {
  generation: number;
  sessionId: string;
  refreshToken: string;
  promise: Promise<string>;
}

interface AuthSessionManagerOptions {
  storage: SessionStorageLike;
  redirectToLogin?: () => void;
}

export class AuthSessionExpiredError extends Error {
  constructor(message = SESSION_EXPIRED_MESSAGE) {
    super(message);
    this.name = 'AuthSessionExpiredError';
  }
}

export class AuthSessionStaleError extends Error {
  constructor() {
    super('登录状态已更新，请重试');
    this.name = 'AuthSessionStaleError';
  }
}

export const isAuthSessionExpiredError = (
  error: unknown,
): error is AuthSessionExpiredError => error instanceof AuthSessionExpiredError;

export const isAuthSessionStaleError = (
  error: unknown,
): error is AuthSessionStaleError => error instanceof AuthSessionStaleError;

export const isSessionExpiredCode = (code: unknown): boolean => (
  code !== undefined
  && code !== null
  && SESSION_EXPIRED_CODES.has(String(code).trim().toUpperCase())
);

/**
 * 认证会话的小型状态机。
 *
 * generation 防止同标签页内的旧异步任务覆盖新登录；sessionId 补足
 * 跨标签页会话识别。access/refresh token 轮换不改变稳定会话身份。
 */
export function createAuthSessionManager({
  storage,
  redirectToLogin,
}: AuthSessionManagerOptions) {
  let generation = 0;
  let refreshFlight: RefreshFlight | null = null;
  let expiryRedirected = false;
  const resetListeners = new Set<() => void>();

  const notifySessionReset = () => {
    resetListeners.forEach((listener) => {
      try {
        listener();
      } catch {
        // Session invalidation must remain fail-closed even if a consumer's
        // local cleanup hook fails.
      }
    });
  };

  const newSessionId = (): string => {
    if (typeof crypto !== 'undefined' && typeof crypto.randomUUID === 'function') {
      return crypto.randomUUID();
    }
    return `${Date.now().toString(36)}-${Math.random().toString(36).slice(2)}`;
  };

  const currentSessionId = (): string | null => {
    const refreshToken = storage.getItem(REFRESH_TOKEN_KEY);
    if (!refreshToken) return null;
    let sessionId = storage.getItem(SESSION_ID_KEY);
    if (!sessionId) {
      // One-time migration for sessions created by the pre-rotation client.
      sessionId = newSessionId();
      storage.setItem(SESSION_ID_KEY, sessionId);
    }
    return sessionId;
  };

  const capture = (): AuthSessionSnapshot => ({
    generation,
    sessionId: currentSessionId(),
    accessToken: storage.getItem(ACCESS_TOKEN_KEY),
    refreshToken: storage.getItem(REFRESH_TOKEN_KEY),
  });

  const isCurrent = (snapshot: AuthSessionSnapshot): boolean => (
    generation === snapshot.generation
    && currentSessionId() === snapshot.sessionId
  );

  const assertCurrent = (snapshot: AuthSessionSnapshot): void => {
    if (!isCurrent(snapshot)) {
      throw new AuthSessionStaleError();
    }
  };

  const clearStoredValues = () => {
    storage.removeItem(ACCESS_TOKEN_KEY);
    storage.removeItem(REFRESH_TOKEN_KEY);
    storage.removeItem(SESSION_ID_KEY);
    SESSION_PROFILE_KEYS.forEach((key) => storage.removeItem(key));
  };

  const clear = (snapshot?: AuthSessionSnapshot): boolean => {
    if (snapshot && !isCurrent(snapshot)) {
      return false;
    }

    generation += 1;
    clearStoredValues();
    notifySessionReset();
    return true;
  };

  const expire = (snapshot: AuthSessionSnapshot): boolean => {
    if (!clear(snapshot)) {
      return false;
    }

    if (!expiryRedirected) {
      expiryRedirected = true;
      redirectToLogin?.();
    }
    return true;
  };

  const replaceTokens = (accessToken: string, refreshToken: string): void => {
    generation += 1;
    clearStoredValues();
    notifySessionReset();
    storage.setItem(ACCESS_TOKEN_KEY, accessToken);
    storage.setItem(REFRESH_TOKEN_KEY, refreshToken);
    storage.setItem(SESSION_ID_KEY, newSessionId());
    expiryRedirected = false;
  };

  const refreshOnce = (
    snapshot: AuthSessionSnapshot,
    refresh: (refreshToken: string) => Promise<RotatedTokenPair>,
  ): Promise<string> => {
    assertCurrent(snapshot);
    if (!snapshot.refreshToken) {
      return Promise.reject(new AuthSessionExpiredError());
    }

    const storedRefreshToken = storage.getItem(REFRESH_TOKEN_KEY);
    if (storedRefreshToken !== snapshot.refreshToken) {
      // This logical session has already been rotated by a sibling request (or
      // another tab). Reuse the committed access token instead of replaying the
      // consumed refresh token and revoking the server-side family.
      const committedAccessToken = storage.getItem(ACCESS_TOKEN_KEY);
      if (storedRefreshToken && committedAccessToken) {
        return Promise.resolve(committedAccessToken);
      }
      return Promise.reject(new AuthSessionExpiredError());
    }

    if (
      refreshFlight
      && refreshFlight.generation === snapshot.generation
      && refreshFlight.sessionId === snapshot.sessionId
      && refreshFlight.refreshToken === snapshot.refreshToken
    ) {
      return refreshFlight.promise;
    }

    const promise: Promise<string> = Promise.resolve()
      .then(() => refresh(snapshot.refreshToken!))
      .then((nextTokens) => {
        assertCurrent(snapshot);
        if (!nextTokens.accessToken || !nextTokens.refreshToken) {
          throw new AuthSessionExpiredError();
        }
        // Rotation belongs to the same logical session. Do not advance the
        // generation or sessionId, otherwise sibling requests would be
        // incorrectly classified as stale.
        storage.setItem(ACCESS_TOKEN_KEY, nextTokens.accessToken);
        storage.setItem(REFRESH_TOKEN_KEY, nextTokens.refreshToken);
        return nextTokens.accessToken;
      })
      .catch((error: unknown) => {
        assertCurrent(snapshot);
        throw error;
      })
      .finally(() => {
        if (refreshFlight?.promise === promise) {
          refreshFlight = null;
        }
      });

    refreshFlight = {
      generation: snapshot.generation,
      sessionId: snapshot.sessionId!,
      refreshToken: snapshot.refreshToken,
      promise,
    };
    return promise;
  };

  return {
    capture,
    isCurrent,
    assertCurrent,
    clear,
    expire,
    replaceTokens,
    refreshOnce,
    subscribeToResets: (listener: () => void) => {
      resetListeners.add(listener);
      return () => resetListeners.delete(listener);
    },
  };
}

const browserStorage: SessionStorageLike = {
  getItem: (key) => (typeof localStorage === 'undefined' ? null : localStorage.getItem(key)),
  setItem: (key, value) => {
    if (typeof localStorage !== 'undefined') localStorage.setItem(key, value);
  },
  removeItem: (key) => {
    if (typeof localStorage !== 'undefined') localStorage.removeItem(key);
  },
};

export const authSession = createAuthSessionManager({
  storage: browserStorage,
  redirectToLogin: () => {
    if (typeof window === 'undefined') return;
    const path = window.location.pathname.replace(/\/+$/, '') || '/';
    if (path !== '/login') {
      window.location.replace('/login');
    }
  },
});
