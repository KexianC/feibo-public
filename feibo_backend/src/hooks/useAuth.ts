import { useState, useEffect, useCallback } from 'react';
import { AuthService, LoginRequest, SmsLoginRequest, UserInfo } from '../services/auth';

interface AuthState {
  isAuthenticated: boolean;
  user: UserInfo | null;
  isLoading: boolean;
  error: string | null;
}

interface UseAuthReturn extends AuthState {
  login: (credentials: LoginRequest | SmsLoginRequest) => Promise<boolean>;
  logout: () => Promise<void>;
  refreshUser: () => Promise<void>;
  isAdmin: () => boolean;
  clearError: () => void;
}

export const useAuth = (): UseAuthReturn => {
  const [state, setState] = useState<AuthState>({
    isAuthenticated: false,
    user: null,
    isLoading: true,
    error: null,
  });

  // 初始化认证状态
  const initializeAuth = useCallback(async () => {
    try {
      setState(prev => ({ ...prev, isLoading: true, error: null }));
      
      const isAuthenticated = AuthService.isAuthenticated();
      const storedUser = AuthService.getStoredUserInfo();
      
      if (isAuthenticated && storedUser && AuthService.isAdmin()) {
        // 直接使用存储的用户信息，避免不必要的API调用
        setState({
          isAuthenticated: true,
          user: storedUser,
          isLoading: false,
          error: null,
        });
      } else {
        if (isAuthenticated || storedUser) {
          AuthService.invalidateSession();
        }
        setState({
          isAuthenticated: false,
          user: null,
          isLoading: false,
          error: null,
        });
      }
    } catch (error) {
      console.error('Initialize auth failed');
      setState({
        isAuthenticated: false,
        user: null,
        isLoading: false,
        error: error instanceof Error ? error.message : '初始化认证状态失败',
      });
    }
  }, []);

  // 登录
  const login = useCallback(async (credentials: LoginRequest | SmsLoginRequest): Promise<boolean> => {
    try {
      setState(prev => ({ ...prev, isLoading: true, error: null }));
      
      const response = 'verificationCode' in credentials
        ? await AuthService.smsLogin(credentials)
        : await AuthService.login(credentials);
      
      // 两种登录方式均已在 AuthService 中完成管理员权限检查
      // 如果能执行到这里，说明登录成功且有管理员权限
      const userInfo: UserInfo = {
        userId: response.userId,
        nickName: response.nickName,
        avatar: response.avatar,
        phoneNumber: response.phoneNumber,
        gender: response.gender,
        roles: response.roles,
        permissions: response.permissions,
      };
      
      setState({
        isAuthenticated: true,
        user: userInfo,
        isLoading: false,
        error: null,
      });
      
      return true;
    } catch (error) {
      console.error('Login failed');
      setState(prev => ({
        ...prev,
        isLoading: false,
        error: error instanceof Error ? error.message : '登录失败',
      }));
      return false;
    }
  }, []);

  // 登出
  const logout = useCallback(async () => {
    try {
      setState(prev => ({ ...prev, isLoading: true }));
      await AuthService.logout();
      setState({
        isAuthenticated: false,
        user: null,
        isLoading: false,
        error: null,
      });
    } catch {
      console.error('Logout failed');
      // 即使登出失败，也要清除本地状态
      setState({
        isAuthenticated: false,
        user: null,
        isLoading: false,
        error: null,
      });
    }
  }, []);

  // 刷新用户信息
  const refreshUser = useCallback(async () => {
    try {
      setState(prev => ({ ...prev, isLoading: true, error: null }));
      // 优先使用存储的用户信息，避免不必要的API调用
      const storedUser = AuthService.getStoredUserInfo();
      if (storedUser) {
        setState(prev => ({
          ...prev,
          user: storedUser,
          isLoading: false,
          error: null,
        }));
      } else {
        // 只有在没有存储信息时才调用API
        const user = await AuthService.getCurrentUser();
        setState(prev => ({
          ...prev,
          user,
          isLoading: false,
        }));
      }
    } catch (error) {
      console.error('Refresh user failed');
      setState(prev => ({
        ...prev,
        isLoading: false,
        error: error instanceof Error ? error.message : '刷新用户信息失败',
      }));
    }
  }, []);

  // 检查是否为管理员
  const isAdmin = useCallback((): boolean => {
    return AuthService.isAdmin();
  }, []);

  // 清除错误
  const clearError = useCallback(() => {
    setState(prev => ({ ...prev, error: null }));
  }, []);

  // 组件挂载时初始化认证状态
  useEffect(() => {
    initializeAuth();
  }, [initializeAuth]);

  return {
    ...state,
    login,
    logout,
    refreshUser,
    isAdmin,
    clearError,
  };
};
