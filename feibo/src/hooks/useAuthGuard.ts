import { useCallback, useEffect, useRef, useState } from 'react';
import { isLoggedIn, logout } from '../services/authApi';

interface UseAuthGuardOptions {
  // 是否在检测到未登录时自动重定向
  autoRedirect?: boolean;
  // 检查间隔（毫秒），默认30秒
  checkInterval?: number;
  // 未登录时的回调函数
  onUnauthorized?: () => void;
}

interface UseAuthGuardReturn {
  // 当前登录状态
  isAuthenticated: boolean;
  // 是否正在检查登录状态
  isChecking: boolean;
  // 手动检查登录状态
  checkAuth: () => void;
  // 登出函数
  handleLogout: () => void;
}

/**
 * 登录状态守卫Hook
 * 用于在组件中检查和监控用户登录状态
 */
export const useAuthGuard = (options: UseAuthGuardOptions = {}): UseAuthGuardReturn => {
  const {
    autoRedirect = false,
    checkInterval = 30000, // 30秒
    onUnauthorized
  } = options;

  const [isAuthenticated, setIsAuthenticated] = useState<boolean>(false);
  const [isChecking, setIsChecking] = useState<boolean>(true);
  const onUnauthorizedRef = useRef(onUnauthorized);

  useEffect(() => {
    onUnauthorizedRef.current = onUnauthorized;
  }, [onUnauthorized]);

  // 检查登录状态的函数
  const checkAuth = useCallback(() => {
    setIsChecking(true);
    try {
      const authStatus = isLoggedIn();
      setIsAuthenticated(authStatus);
      
      // 如果未登录且设置了回调函数，执行回调
      if (!authStatus) {
        onUnauthorizedRef.current?.();
      }
      
      // 如果未登录且设置了自动重定向，刷新页面回到登录界面
      if (!authStatus && autoRedirect) {
        console.log('用户未登录，准备重定向到登录页面');
        // 清除认证信息并使用 replace，避免返回到已失效页面。
        logout();
        window.location.replace('/login');
      }
    } catch (error) {
      console.error('检查登录状态失败:', error);
      setIsAuthenticated(false);
      
      // 发生错误时也执行未授权回调
      onUnauthorizedRef.current?.();
    } finally {
      setIsChecking(false);
    }
  }, [autoRedirect]);

  // 登出处理函数
  const handleLogout = () => {
    logout();
    setIsAuthenticated(false);
    window.location.replace('/login');
  };

  // 组件挂载时立即检查登录状态
  useEffect(() => {
    checkAuth();
  }, [checkAuth]);

  // 设置定期检查登录状态
  useEffect(() => {
    if (checkInterval > 0) {
      const intervalId = setInterval(() => {
        // 只有在当前认为已登录时才进行检查，避免频繁检查未登录状态
        if (isAuthenticated) {
          checkAuth();
        }
      }, checkInterval);

      return () => clearInterval(intervalId);
    }
  }, [checkAuth, checkInterval, isAuthenticated]);

  // 监听storage变化，当其他标签页登出时同步状态
  useEffect(() => {
    const handleStorageChange = (e: StorageEvent) => {
      // 监听access-token的变化
      if (e.key === 'access-token') {
        if (!e.newValue && e.oldValue) {
          // token被删除，用户已登出
          console.log('检测到其他标签页登出，同步登录状态');
          setIsAuthenticated(false);
          onUnauthorizedRef.current?.();
        } else if (e.newValue && !e.oldValue) {
          // token被添加，用户已登录
          console.log('检测到其他标签页登录，同步登录状态');
          setIsAuthenticated(true);
        }
      }
    };

    window.addEventListener('storage', handleStorageChange);
    return () => window.removeEventListener('storage', handleStorageChange);
  }, []);

  return {
    isAuthenticated,
    isChecking,
    checkAuth,
    handleLogout
  };
};

export default useAuthGuard;
