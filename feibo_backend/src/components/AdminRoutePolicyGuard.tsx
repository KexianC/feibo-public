import React from 'react';
import { Button, Result } from 'antd';
import { useLocation, useNavigate } from 'react-router-dom';
import { AuthService } from '@/services/auth';
import {
  canAccessAdminRoute,
  getAdminRouteRequirement,
  readStoredAdminCapabilityCodes,
} from '@/config/adminRoutePolicy';

interface AdminRoutePolicyGuardProps {
  children: React.ReactNode;
}

const AdminRoutePolicyGuard: React.FC<AdminRoutePolicyGuardProps> = ({ children }) => {
  const location = useLocation();
  const navigate = useNavigate();
  const accessContext = {
    isAdmin: AuthService.isAdmin(),
    isSuperAdmin: AuthService.isSuperAdmin(),
    permissionCodes: readStoredAdminCapabilityCodes(),
  };

  if (canAccessAdminRoute(location.pathname, accessContext)) {
    return <>{children}</>;
  }

  const requirement = getAdminRouteRequirement(location.pathname);
  const isDisabled = requirement === 'disabled';
  const isSuperAdminOnly = requirement === 'super-admin';

  return (
    <Result
      status="403"
      title={isDisabled ? '功能暂未开放' : '权限不足'}
      subTitle={isDisabled
        ? '该模块当前已停用，请返回后台首页。'
        : isSuperAdminOnly
          ? '该页面仅限超级管理员访问。'
          : '当前账号没有访问该模块所需权限。'}
      extra={<Button type="primary" onClick={() => navigate('/dashboard', { replace: true })}>返回首页</Button>}
    />
  );
};

export default AdminRoutePolicyGuard;
