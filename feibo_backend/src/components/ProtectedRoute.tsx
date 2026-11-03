import React, { useEffect } from 'react';
import { Navigate } from 'react-router-dom';
import { useAuth } from '../hooks/useAuth';
import { AuthService } from '../services/auth';

interface ProtectedRouteProps {
  children: React.ReactNode;
}

const ProtectedRoute: React.FC<ProtectedRouteProps> = ({ children }) => {
  const { isAuthenticated, isLoading, isAdmin } = useAuth();
  const hasAdminAccess = isAdmin();

  useEffect(() => {
    if (!isLoading && isAuthenticated && !hasAdminAccess) {
      AuthService.invalidateSession();
    }
  }, [hasAdminAccess, isAuthenticated, isLoading]);

  // 如果正在加载认证状态，显示加载页面
  if (isLoading) {
    return (
      <div style={{
        display: 'flex',
        justifyContent: 'center',
        alignItems: 'center',
        height: '100vh',
        fontSize: '16px',
        color: '#666'
      }}>
        正在验证身份...
      </div>
    );
  }

  // 如果未登录，重定向到登录页
  if (!isAuthenticated) {
    return <Navigate to="/login" replace />;
  }

  // 本地用户信息缺失或管理员角色已失效时结束旧会话，避免卡在无权限页。
  if (!hasAdminAccess) {
    return <Navigate to="/login" replace />;
  }

  // 认证通过且是管理员，渲染子组件
  return <>{children}</>;
};

export default ProtectedRoute;
