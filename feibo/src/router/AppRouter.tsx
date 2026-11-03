import { lazy, Suspense } from 'react';
import { Navigate, Route, Routes } from 'react-router-dom';
import AppLoader from '@/components/AppLoader';
import ProtectedLayout from '@/layouts/ProtectedLayout';
import { useAuth } from '@/providers/AuthProvider';
import { ROUTE_PATHS } from '@/router/routePaths';

const LoginPage = lazy(() => import('@/pages/auth/LoginPage'));
const ZhibiPage = lazy(() => import('@/pages/tools/ZhibiPage'));
const VideoGenerationPage = lazy(() => import('@/pages/tools/VideoGenerationPage'));
const ZhihePage = lazy(() => import('@/pages/tools/ZhihePage'));
const WorkbenchPage = lazy(() => import('@/pages/workbench/WorkbenchPage'));
const AccountPage = lazy(() => import('@/pages/account/AccountPage'));
const PublishPage = lazy(() => import('@/pages/publish/PublishPage'));

function RequireAuth() {
  const { isAuthenticated, isInitialized, isChecking } = useAuth();

  if (!isInitialized || isChecking) {
    return <AppLoader message={!isInitialized ? '正在加载应用...' : '正在验证登录状态...'} />;
  }

  if (!isAuthenticated) {
    return <Navigate to={ROUTE_PATHS.login} replace />;
  }

  return <ProtectedLayout />;
}

function RouteFallback() {
  return <AppLoader message="正在切换页面..." />;
}

export default function AppRouter() {
  return (
    <Suspense fallback={<RouteFallback />}>
      <Routes>
        <Route path={ROUTE_PATHS.root} element={<Navigate to={ROUTE_PATHS.zhibi} replace />} />
        <Route path={ROUTE_PATHS.login} element={<LoginPage />} />
        <Route element={<RequireAuth />}>
          <Route path={ROUTE_PATHS.videoGeneration} element={<VideoGenerationPage />} />
          <Route path={ROUTE_PATHS.zhibi} element={<ZhibiPage />} />
          <Route path={ROUTE_PATHS.zhihe} element={<ZhihePage />} />
          <Route path={ROUTE_PATHS.synthesis} element={<WorkbenchPage />} />
          <Route path={ROUTE_PATHS.account} element={<AccountPage />} />
          <Route path={ROUTE_PATHS.publish} element={<PublishPage />} />
        </Route>
        <Route path="*" element={<Navigate to={ROUTE_PATHS.root} replace />} />
      </Routes>
    </Suspense>
  );
}
