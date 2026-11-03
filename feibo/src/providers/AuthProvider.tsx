import {
  createContext,
  useCallback,
  useContext,
  useEffect,
  useMemo,
  useState,
  type ReactNode,
} from 'react';
import { useAuthGuard } from '@/hooks/useAuthGuard';
import { API_CONFIG } from '@/config/api';
import { getWebSocketService } from '@/services/websocketService';
import {
  type ActivityStatusResponse,
} from '@/services/userApi';
import {
  isLoggedIn as checkAuthStatus,
  logout as authLogout,
  setupTokenRefreshInterceptor,
} from '@/services/authApi';
import { STORAGE_KEYS, type User } from '@/types';
import { useActions } from '@/store';

interface AuthContextValue {
  user: User | null;
  isAuthenticated: boolean;
  isInitialized: boolean;
  isChecking: boolean;
  showPromotionModal: boolean;
  activityStatus: ActivityStatusResponse | null;
  isLoadingActivity: boolean;
  login: (user: User) => Promise<void>;
  logout: () => void;
  updateUser: (patch: Partial<User>) => void;
  closePromotionModal: () => void;
  claimPromotionReward: () => Promise<void>;
}

const AuthContext = createContext<AuthContextValue | null>(null);

function saveUserState(userData: User | null, loginState: boolean) {
  try {
    const persistedUser = userData
      ? (({
          accessToken: _accessToken,
          refreshToken: _refreshToken,
          token: _token,
          tokenHead: _tokenHead,
          ...profile
        }) => profile)(userData)
      : null;
    const authData = {
      user: persistedUser,
      isLoggedIn: loginState,
      timestamp: new Date().toISOString(),
    };

    localStorage.setItem(STORAGE_KEYS.USER_AUTH, JSON.stringify(authData));
    if (persistedUser) {
      localStorage.setItem('user-info', JSON.stringify(persistedUser));
    } else {
      localStorage.removeItem('user-info');
    }
  } catch (error) {
    console.error('保存用户状态失败:', error);
  }
}

function clearUserState() {
  localStorage.removeItem(STORAGE_KEYS.USER_AUTH);
  localStorage.removeItem('user-info');
}

function restoreUserState() {
  try {
    if (!checkAuthStatus()) {
      clearUserState();
      return null;
    }

    const authData = localStorage.getItem(STORAGE_KEYS.USER_AUTH);
    if (!authData) {
      return null;
    }

    const { user: savedUser, isLoggedIn, timestamp } = JSON.parse(authData) as {
      user: User | null;
      isLoggedIn: boolean;
      timestamp: string;
    };

    const loginTime = new Date(timestamp);
    const hoursDiff = (Date.now() - loginTime.getTime()) / (1000 * 60 * 60);

    if (hoursDiff >= 24 || !savedUser || !isLoggedIn) {
      authLogout();
      clearUserState();
      return null;
    }

    if (savedUser.loginTime) {
      savedUser.loginTime = new Date(savedUser.loginTime);
    }

    return savedUser;
  } catch (error) {
    console.error('恢复用户状态失败:', error);
    authLogout();
    clearUserState();
    return null;
  }
}

async function connectWebSocket() {
  try {
    const wsService = getWebSocketService({
      url: API_CONFIG.WS_URL,
      heartbeatInterval: 30000,
      reconnectInterval: 5000,
      maxReconnectAttempts: 5,
      debug: false,
    });

    await wsService.connect();
  } catch (error) {
    console.warn('WebSocket 连接失败:', error);
  }
}

export function AuthProvider({ children }: { children: ReactNode }) {
  const actions = useActions();
  const [user, setUser] = useState<User | null>(null);
  const [isLoggedIn, setIsLoggedIn] = useState(false);
  const [isInitialized, setIsInitialized] = useState(false);
  const [showPromotionModal, setShowPromotionModal] = useState(false);
  const [activityStatus, setActivityStatus] = useState<ActivityStatusResponse | null>(null);
  const [isLoadingActivity, setIsLoadingActivity] = useState(false);

  const clearLocalAuth = useCallback(() => {
    setUser(null);
    setIsLoggedIn(false);
    clearUserState();
  }, []);

  const { isAuthenticated: globalAuthStatus, isChecking, checkAuth } = useAuthGuard({
    autoRedirect: false,
    checkInterval: 60000,
    onUnauthorized: clearLocalAuth,
  });

  useEffect(() => {
    const initializeApp = async () => {
      const restoredUser = restoreUserState();
      if (restoredUser) {
        setUser(restoredUser);
        setIsLoggedIn(true);
        await connectWebSocket();
      }

      actions.initializeAppState();
      setIsInitialized(true);
    };

    const cleanupRefreshInterceptor = setupTokenRefreshInterceptor();
    void initializeApp();

    return cleanupRefreshInterceptor;
  }, [actions]);

  useEffect(() => {
    if (isInitialized) {
      saveUserState(user, isLoggedIn);
    }
  }, [isInitialized, isLoggedIn, user]);

  useEffect(() => {
    if (!isInitialized || isChecking) {
      return;
    }

    if (!globalAuthStatus && isLoggedIn) {
      const timeoutId = window.setTimeout(() => {
        if (!checkAuthStatus()) {
          clearLocalAuth();
        }
      }, 500);

      return () => window.clearTimeout(timeoutId);
    }

    if (globalAuthStatus && !isLoggedIn) {
      const restoredUser = restoreUserState();
      if (restoredUser) {
        setUser(restoredUser);
        setIsLoggedIn(true);
      }
    }
  }, [clearLocalAuth, globalAuthStatus, isChecking, isInitialized, isLoggedIn]);

  const login = useCallback(
    async (nextUser: User) => {
      setUser(nextUser);
      setIsLoggedIn(true);

      // The free plan is automatic and permanent; no promotional claim is required.
      setShowPromotionModal(false);

      window.setTimeout(() => {
        checkAuth();
      }, 100);
    },
    [checkAuth],
  );

  const logout = useCallback(() => {
    authLogout();
    clearLocalAuth();
  }, [clearLocalAuth]);

  const updateUser = useCallback((patch: Partial<User>) => {
    setUser((currentUser) => {
      if (!currentUser) return currentUser;
      return {
        ...currentUser,
        ...patch,
        userId: String(patch.userId ?? currentUser.userId),
      };
    });
  }, []);

  const closePromotionModal = useCallback(() => {
    setShowPromotionModal(false);
    localStorage.setItem('promotion-modal-shown', 'true');
  }, []);

  const claimPromotionReward = useCallback(async () => {
    closePromotionModal();
  }, [closePromotionModal]);

  const value = useMemo<AuthContextValue>(
    () => ({
      user,
      isAuthenticated: isLoggedIn && globalAuthStatus,
      isInitialized,
      isChecking,
      showPromotionModal,
      activityStatus,
      isLoadingActivity,
      login,
      logout,
      updateUser,
      closePromotionModal,
      claimPromotionReward,
    }),
    [
      activityStatus,
      claimPromotionReward,
      closePromotionModal,
      globalAuthStatus,
      isChecking,
      isInitialized,
      isLoadingActivity,
      isLoggedIn,
      login,
      logout,
      updateUser,
      showPromotionModal,
      user,
    ],
  );

  return <AuthContext.Provider value={value}>{children}</AuthContext.Provider>;
}

export function useAuth() {
  const context = useContext(AuthContext);

  if (!context) {
    throw new Error('useAuth must be used within AuthProvider');
  }

  return context;
}
