import { useEffect } from 'react';
import { Outlet } from 'react-router-dom';
import AppLoader from '@/components/AppLoader';
import Header from '@/components/Header';
import PromotionModal from '@/components/PromotionModal';
import { useViewportScale, VIEWPORT_BASE_SIZE } from '@/hooks/useViewportScale';
import { useAuth } from '@/providers/AuthProvider';
import { useCurrentTheme } from '@/store';

function getThemeClasses(theme: 'classic' | 'dark') {
  if (theme === 'dark') {
    return 'app-shell text-white';
  }

  return 'app-shell text-gray-900';
}

export default function ProtectedLayout() {
  const scale = useViewportScale();
  const theme = useCurrentTheme();
  const {
    user,
    isInitialized,
    isChecking,
    showPromotionModal,
    activityStatus,
    isLoadingActivity,
    logout,
    updateUser,
    closePromotionModal,
    claimPromotionReward,
  } = useAuth();

  useEffect(() => {
    const documentElement = document.documentElement;
    documentElement.classList.remove('theme-classic', 'theme-dark', 'dark');
    documentElement.classList.add(theme === 'dark' ? 'theme-dark' : 'theme-classic');
    documentElement.classList.toggle('dark', theme === 'dark');
  }, [theme]);

  if (!isInitialized || isChecking) {
    return <AppLoader message={!isInitialized ? '正在加载应用...' : '正在验证登录状态...'} />;
  }

  return (
    <div className={`flex h-screen w-screen items-center justify-center overflow-hidden ${getThemeClasses(theme)}`}>
      <div
        style={{
          width: VIEWPORT_BASE_SIZE.width,
          height: VIEWPORT_BASE_SIZE.height,
          zoom: scale.x,
        }}
      >
        <div className="h-full w-full flex flex-col p-5">
          <div className="flex-shrink-0">
            <Header user={user} onLogout={logout} onUserUpdate={updateUser} />
          </div>
          <main className="min-h-0 flex-1 overflow-hidden rounded-b-lg border-t-0 app-surface">
            <Outlet />
          </main>
          <PromotionModal
            isOpen={showPromotionModal}
            onClose={closePromotionModal}
            onClaim={claimPromotionReward}
            activityStatus={activityStatus}
            isLoading={isLoadingActivity}
          />
        </div>
      </div>
    </div>
  );
}
