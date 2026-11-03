import React, { Suspense, lazy, useEffect, useMemo, useRef, useState } from 'react';
import { 
  UserCircle2,
  Users2,
  KeyRound,
  LogOut,
  Moon,
  Sun,
  Layers3,
  BookOpen,
  Crown,
  Clock4,
  Zap
} from 'lucide-react';
import { useLocation, useNavigate } from 'react-router-dom';
import { useActions, useCurrentTheme } from '../store';
import EntitlementSummary from './EntitlementSummary';
import { ROUTE_PATHS } from '@/router/routePaths';
import EditorLoadingFallback from '@/features/editor/components/EditorLoadingFallback';

const SubAccountManagement = lazy(() => import('./SubAccountManagement'));
const ChangePassword = lazy(() => import('./ChangePassword'));
const PersonalInfo = lazy(() => import('./PersonalInfo'));
const RechargeModal = lazy(() => import('./RechargeModal'));

// 为了兼容性，定义Header组件使用的User类型
interface HeaderUser {
  username?: string;
  nickName?: string;
  loginTime?: Date;
  token?: string;
  userId?: string;
  avatar?: string;
  phoneNumber?: string;
  gender?: string;
}

interface User {
  username: string;
  loginTime: Date;
}

interface HeaderProps {
  user: HeaderUser | null;
  onLogout: () => void;
  onUserUpdate: (patch: Partial<HeaderUser>) => void;
}

const Header: React.FC<HeaderProps> = ({ user, onLogout, onUserUpdate }) => {
  const currentTheme = useCurrentTheme();
  const { setTheme } = useActions();
  const navigate = useNavigate();
  const location = useLocation();
  const [showUserMenu, setShowUserMenu] = useState(false);
  const [showSubAccountManagement, setShowSubAccountManagement] = useState(false);
  const [showChangePassword, setShowChangePassword] = useState(false);
  const [showPersonalInfo, setShowPersonalInfo] = useState(false);
  const [showRecharge, setShowRecharge] = useState(false);
  const userMenuRef = useRef<HTMLDivElement>(null);
  const navigationItems = useMemo(
    () => [
      {
        key: ROUTE_PATHS.zhibi,
        label: '智笔医言',
        englishLabel: 'MEDSCRIBE',
      },
      {
        key: ROUTE_PATHS.zhihe,
        label: '智核医审',
        englishLabel: 'MEDPROOF',
      },
      {
        key: ROUTE_PATHS.synthesis,
        label: '智绘医合',
        englishLabel: 'MEDVID',
      },
    ],
    [],
  );
  
  // 点击外部关闭下拉菜单
  useEffect(() => {
    const handleClickOutside = (event: MouseEvent) => {
      if (userMenuRef.current && !userMenuRef.current.contains(event.target as Node)) {
        setShowUserMenu(false);
      }
    };

    document.addEventListener('mousedown', handleClickOutside);
    return () => {
      document.removeEventListener('mousedown', handleClickOutside);
    };
  }, []);

  const handleUserMenuToggle = () => {
    setShowUserMenu(!showUserMenu);
  };

  const handleSubAccountManagement = () => {
    setShowSubAccountManagement(true);
    setShowUserMenu(false);
  };

  // 根据用户信息判断用户角色
  const getUserRole = (): 'expert' | 'assistant' => {
    // 这里可以根据实际的用户角色字段来判断
    // 目前先默认返回expert，后续可以根据实际需求调整
    // 可能的判断逻辑：
    // - 根据user.role字段
    // - 根据用户权限
    // - 根据API返回的用户类型
    return 'expert';
  };

  const handleChangePassword = () => {
    setShowChangePassword(true);
    setShowUserMenu(false);
  };

  const handlePersonalInfo = () => {
    setShowPersonalInfo(true);
    setShowUserMenu(false);
  };

  // 处理用户信息更新
  const handleUserInfoUpdate = (patch: Partial<HeaderUser>) => {
    const nextPatch = { ...patch };
    if (patch.nickName !== undefined && patch.username === undefined) {
      nextPatch.username = patch.nickName;
    }
    onUserUpdate(nextPatch);
    // 重新获取用户资源信息以更新界面

  };

  const handleLogout = () => {
    onLogout();
    setShowUserMenu(false);
  };

  const handleThemeToggle = () => {
    setTheme(currentTheme === 'dark' ? 'classic' : 'dark');
    setShowUserMenu(false);
  };

  const activePath = location.pathname === ROUTE_PATHS.videoGeneration
    ? ROUTE_PATHS.synthesis
    : navigationItems.find((item) => location.pathname.startsWith(item.key))?.key;

  return (
    <header className="app-topbar relative z-20 flex h-[72px] w-full items-center justify-between rounded-t-lg px-5">
      {/* 左侧Logo */}
      <div className="flex min-w-[220px] items-center">
        <img
          className="h-10 w-auto max-w-[220px] object-contain"
          src="/img/logo_d.png"
          alt="飞驳"
          style={{
            filter: currentTheme === 'classic' ? 'brightness(0) saturate(100%)' : undefined,
          }}
        />
      </div>
      
      {/* 中间菜单区域 */}
      <nav className="flex flex-1 items-center justify-center">
        <div className="flex items-center gap-12">
          {navigationItems.map((item) => (
            <button
              key={item.key}
              onClick={() => navigate(item.key)}
              className={`relative flex min-w-[112px] flex-col items-center px-2 py-2 text-sm transition-colors ${
                activePath === item.key
                  ? 'text-teal-700 dark:text-teal-300'
                  : 'text-slate-600 hover:text-slate-950 dark:text-slate-300 dark:hover:text-white'
              }`}
            >
              <span className="text-[21px] font-semibold leading-none">{item.label}</span>
              <span className="mt-0.5 text-[10px] font-medium text-slate-400">
                {item.englishLabel}
              </span>
              {activePath === item.key && (
                <span className="absolute -bottom-1 h-0.5 w-8 rounded-full bg-teal-600 dark:bg-teal-300" />
              )}
            </button>
          ))}
        </div>
      </nav>

      {/* 右侧图标区域 */}
      <div className="flex min-w-[220px] items-center justify-end">
        <div className="relative z-10 flex items-center gap-1 rounded-lg bg-white/70 p-1 ring-1 ring-slate-200 dark:bg-slate-900/70 dark:ring-slate-700">
          {/* 层叠图标 - 子账号管理 */}
          <button
            onClick={() => setShowSubAccountManagement(true)}
            className="app-icon-button"
            title="子账号管理"
          >
            <Layers3 className="w-5 h-5" />
          </button>
          
          {/* 教程按钮 */}
          <a
            href="https://doc.weixin.qq.com/doc/w3_AesAwQbtADICNuPmOhZViSlytO87O?scode=AG4AogcTAAox5d7ci6AesAwQbtADI"
            target="_blank"
            rel="noopener noreferrer"
            className="app-icon-button"
            title="使用教程"
          >
            <BookOpen className="w-5 h-5" />
          </a>
          
          {/* 用户菜单 */}
          <div className="relative flex items-center" ref={userMenuRef}>
            <button
              onClick={handleUserMenuToggle}
              className={`app-icon-button ${showUserMenu ? 'bg-teal-50 text-teal-700 dark:bg-teal-950/40 dark:text-teal-300' : ''}`}
              title="账户菜单"
            >
              <UserCircle2 className="w-5 h-5" />
            </button>

            {/* 用户下拉菜单 */}
            {showUserMenu && (
              <div className="absolute right-0 top-full z-50 mt-3 max-h-[80vh] w-72 overflow-y-auto rounded-lg border border-slate-200 bg-white py-1 shadow-2xl shadow-slate-900/15 xs:-right-16 sm:right-0">
                <div className="border-b border-slate-100 px-3 py-3"><EntitlementSummary compact />
                  <button onClick={() => { setShowUserMenu(false); setShowRecharge(true); }} className="mt-2 w-full rounded bg-teal-600 py-2 text-sm font-medium text-white hover:bg-teal-700">会员与充值</button>
                </div>

                <button
                  onClick={handlePersonalInfo}
                  className="flex w-full items-center space-x-2 px-4 py-2.5 text-left text-sm text-slate-700 transition-colors hover:bg-slate-50"
                >
                  <UserCircle2 size={18} className="text-blue-600" />
                  <span>个人信息</span>
                </button>
                <button
                  onClick={handleSubAccountManagement}
                  className="flex w-full items-center space-x-2 px-4 py-2.5 text-left text-sm text-slate-700 transition-colors hover:bg-slate-50"
                >
                  <Users2 size={18} className="text-teal-600" />
                  <span>子账号管理</span>
                </button>
                <button
                  onClick={handleChangePassword}
                  className="flex w-full items-center space-x-2 px-4 py-2.5 text-left text-sm text-slate-700 transition-colors hover:bg-slate-50"
                >
                  <KeyRound size={18} className="text-cyan-600" />
                  <span>修改密码</span>
                </button>
                <button
                  onClick={handleThemeToggle}
                  className="flex w-full items-center space-x-2 px-4 py-2.5 text-left text-sm text-slate-700 transition-colors hover:bg-slate-50"
                >
                  {currentTheme === 'dark' ? (
                    <Sun size={18} className="text-yellow-500" />
                  ) : (
                    <Moon size={18} className="text-indigo-600" />
                  )}
                  <span>{currentTheme === 'dark' ? '切换浅色主题' : '切换深色主题'}</span>
                </button>
                <hr className="my-1 border-gray-200" />
                <button
                  onClick={handleLogout}
                  className="flex w-full items-center space-x-2 px-4 py-2.5 text-left text-sm text-red-600 transition-colors hover:bg-red-50"
                >
                  <LogOut size={18} />
                  <span>退出登录</span>
                </button>
              </div>
            )}
          </div>
        </div>
      </div>
      
      <Suspense fallback={<EditorLoadingFallback label="正在加载账户模块..." className="fixed inset-0 z-50" />}>
        {showRecharge && <RechargeModal isOpen onClose={() => setShowRecharge(false)} />}
        <SubAccountManagement 
          isOpen={showSubAccountManagement} 
          onClose={() => setShowSubAccountManagement(false)}
          userRole={getUserRole()}
        />
        
        <PersonalInfo 
          isOpen={showPersonalInfo} 
          onClose={() => setShowPersonalInfo(false)}
          currentUser={user?.username || user?.nickName}
          onUserInfoUpdate={handleUserInfoUpdate}
        />
        
        <ChangePassword 
          isOpen={showChangePassword} 
          onClose={() => setShowChangePassword(false)}
          currentUser={user?.username}
        />
      </Suspense>
    </header>
  );
};

export default Header;
