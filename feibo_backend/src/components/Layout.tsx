import React, { useEffect, useState } from 'react';
import { Layout as AntLayout, Menu, theme, Tabs, Dropdown, Button, Space, message } from 'antd';
import { useNavigate, useLocation } from 'react-router-dom';

const { Header, Sider, Content } = AntLayout;
import {
  HomeOutlined,
  UserOutlined,
  ShoppingCartOutlined,
  GiftOutlined,
  AppstoreOutlined,
  TeamOutlined,
  MenuFoldOutlined,
  MenuUnfoldOutlined,
  DollarOutlined,
  SafetyOutlined,
  ControlOutlined,
  FileTextOutlined,
  ReloadOutlined,
  DeleteOutlined,
  DownOutlined,
  EllipsisOutlined,
  CloseOutlined,
  LogoutOutlined,
  GlobalOutlined,
} from '@ant-design/icons';
import type { MenuProps } from 'antd';
import { AuthService } from '@/services/auth';
import { canAccessAdminRoute, readStoredAdminCapabilityCodes } from '@/config/adminRoutePolicy';
import ModelFailureNotices from './ModelFailureNotices';

type MenuItem = Required<MenuProps>['items'][number];

interface TabItem {
  key: string;
  label: string;
  icon?: React.ReactNode;
  closable?: boolean;
}

interface LayoutProps {
  children: React.ReactNode;
}

const Layout: React.FC<LayoutProps> = ({ children }) => {
  const [collapsed, setCollapsed] = useState(false);
  const [tabs, setTabs] = useState<TabItem[]>([
    {
      key: '/dashboard',
      label: '首页',
      icon: <HomeOutlined />,
      closable: false,
    },
  ]);
  const [activeTabKey, setActiveTabKey] = useState('/dashboard');
  const [openKeys, setOpenKeys] = useState<string[]>([]);
  const navigate = useNavigate();
  const location = useLocation();
  const {
    token: { colorBgContainer },
  } = theme.useToken();

  // 严格按照参考网站的菜单结构创建
  const menuItems: MenuItem[] = [
    {
      key: 'pages',
      icon: <HomeOutlined />,
      label: '页面',
      children: [
        {
          key: '/dashboard',
          label: '后台首页',
        },
      ],
    },
    {
      key: 'users',
      icon: <UserOutlined />,
      label: '用户管理',
      children: [
        {
          key: '/users/list',
          label: '用户查询',
        },
        {
          key: '/users/manage',
          label: '用户维护',
        },
        {
          key: '/users/conversations',
          label: '对话记录',
        },
        {
          key: '/users/synthesis',
          label: '合成记录',
        },
        {
          key: '/users/feedback',
          label: '用户反馈',
        },
      ],
    },
    {
      key: 'orders',
      icon: <ShoppingCartOutlined />,
      label: '订单管理',
      children: [
        {
          key: '/orders/list',
          label: '订单查询',
        },
        {
          key: '/orders/manage',
          label: '订单维护',
        },
      ],
    },
    {
      key: 'packages',
      icon: <GiftOutlined />,
      label: '会员与充值',
      children: [
        {
          key: '/packages/entitlements',
          label: '免费版与模型分级',
        },
        {
          key: '/packages/plans',
          label: '会员类型',
        },
        {
          key: '/packages/compute',
          label: '充值包',
        },
        {
          key: '/packages/grants',
          label: '账号权益发放',
        },
      ],
    },
    {
      key: 'review',
      icon: <FileTextOutlined />,
      label: '文案审核',
      children: [
        {
          key: '/functions/review/content',
          label: '文案查看',
        },
        {
          key: '/functions/review/expert',
          label: '审核人员配置',
        },
      ],
    },
    {
      key: 'functions',
      icon: <AppstoreOutlined />,
      label: '功能管理',
      children: [
        {
          key: 'copywriting',
          label: '文案助手',
          children: [
            {
              key: '/functions/copywriting/categories',
              label: '默认分类',
            },
          ],
        },
      ],
    },
    {
      key: 'workspace',
      icon: <AppstoreOutlined />,
      label: '工作台',
      children: [
        {
          key: '/functions/workspace/voice',
          label: '配音管理',
        },
        {
          key: '/functions/workspace/background',
          label: '背景管理',
        },
        {
          key: '/functions/workspace/music',
          label: '音乐管理',
        },
        {
          key: '/functions/workspace/video',
          label: '视频管理',
        },
        {
          key: '/functions/workspace/illustration',
          label: '插图管理',
        },
        {
          key: '/functions/workspace/model',
          label: '模型管理',
        },
        {
          key: '/functions/workspace/template',
          label: '模板管理',
        },
        {
          key: '/functions/workspace/cases',
          label: '案例视频',
        },
      ],
    },
    {
      key: 'geo',
      icon: <GlobalOutlined />,
      label: 'GEOAgent',
      children: [
        {
          key: '/geo/medical-management',
          label: '医务管理',
        },
        {
          key: '/geo/knowledge-base',
          label: '知识库管理',
        },
        {
          key: '/geo/workbench',
          label: 'GEO运营',
        },
        {
          key: '/geo/content-publish',
          label: '内容发布',
        },
      ],
    },
    {
      key: 'market',
      icon: <TeamOutlined />,
      label: '市场管理',
      children: [
        {
          key: 'agents',
          label: '代理',
          children: [
            {
              key: '/market/agents/list',
              label: '代理查询',
            },
            {
              key: '/market/agents/manage',
              label: '代理维护',
            },
            {
              key: '/market/agents/level',
              label: '代理等级',
            },
            {
              key: '/market/agents/relation',
              label: '代理关系',
            },
            {
              key: '/market/agents/approval',
              label: '代理审核',
            },
          ],
        },
        {
          key: 'recruitment',
          label: '招商',
          children: [
            {
              key: '/market/recruitment/settings',
              label: '招商设置',
            },
            {
              key: '/market/recruitment/relations',
              label: '招商关系',
            },
          ],
        },
        {
          key: 'operations',
          label: '运营',
          children: [
            {
              key: '/market/operations/management-fee',
              label: '管理费设置',
            },
            {
              key: '/market/operations/rebate',
              label: '达标返点设置',
            },
            {
              key: '/market/operations/partnership-subsidy',
              label: '合伙补贴设置',
            },
          ],
        },
        {
          key: 'partners',
          label: '合伙',
          children: [
            {
              key: '/market/partners/list',
              label: '合伙人列表',
            },
            {
              key: '/market/partners/settings',
              label: '合伙人设置',
            },
            {
              key: '/market/partners/relations',
              label: '合伙人关系',
            },
            {
              key: '/market/partners/approval',
              label: '合伙人审核',
            },
          ],
        },
      ],
    },
    {
      key: 'finance',
      icon: <DollarOutlined />,
      label: '财务管理',
      children: [
        {
          key: 'market-expenses',
          label: '市场费用',
          children: [
            {
              key: '/finance/market-expenses/operation-fee-issue',
              label: '运营费发放',
            },
            {
              key: '/finance/market-expenses/operation-fee-review',
              label: '运营费审核',
            },
            {
              key: '/finance/market-expenses/recruitment-fee-issue',
              label: '招商费发放',
            },
            {
              key: '/finance/market-expenses/recruitment-fee-review',
              label: '招商费审核',
            },
            {
              key: '/finance/market-expenses/regional-fee-issue',
              label: '地区管理费发放',
            },
            {
              key: '/finance/market-expenses/regional-fee-review',
              label: '地区管理费审核',
            },
            {
              key: '/finance/market-expenses/large-regional-fee-issue',
              label: '大区管理费发放',
            },
            {
              key: '/finance/market-expenses/large-regional-fee-review',
              label: '大区管理费审核',
            },
          ],
        },
        {
          key: '/finance/partner-commission',
          label: '合伙佣金',
        },
      ],
    },
    {
      key: 'accounts',
      icon: <SafetyOutlined />,
      label: '账户管理',
      children: [
        {
          key: '/accounts/roles',
          label: '账户角色',
        },
        {
          key: '/accounts/list',
          label: '账户列表',
        },
      ],
    },
    {
      key: 'system',
      icon: <ControlOutlined />,
      label: '系统管理',
      children: [
        {
          key: '/system/info',
          label: '系统监控',
        },
        {
          key: '/system/ai-models',
          label: '智笔医言管理',
        },
        { key: '/system/video-generation', label: 'AI 视频管理' },
      ],
    },
    {
      key: 'logs',
      icon: <FileTextOutlined />,
      label: '日志管理',
      children: [
        {
          key: '/logs/operation',
          label: '操作日志',
        },
        {
          key: '/logs/login',
          label: '登录日志',
        },
      ],
    },
  ];

  const accessContext = {
    isAdmin: AuthService.isAdmin(),
    isSuperAdmin: AuthService.isSuperAdmin(),
    permissionCodes: readStoredAdminCapabilityCodes(),
  };

  const filterMenuItems = (items: MenuItem[]): MenuItem[] => items.flatMap(item => {
    if (!item) return [];

    const candidate = item as Exclude<MenuItem, null> & { children?: MenuItem[] };
    const key = String(candidate.key);
    if (key.startsWith('/') && !canAccessAdminRoute(key, accessContext)) {
      return [];
    }

    if (Array.isArray(candidate.children)) {
      const visibleChildren = filterMenuItems(candidate.children);
      if (visibleChildren.length === 0) return [];
      return [{ ...candidate, children: visibleChildren } as MenuItem];
    }

    return [item];
  });

  const visibleMenuItems = filterMenuItems(menuItems);

  // 添加标签页
  const addTab = (key: string, label: string, icon?: React.ReactNode) => {
    const existingTab = tabs.find(tab => tab.key === key);
    if (!existingTab) {
      const newTab: TabItem = {
        key,
        label,
        icon,
        closable: key !== '/dashboard',
      };
      setTabs(prev => [...prev, newTab]);
    }
    setActiveTabKey(key);
    navigate(key);
  };

  // 关闭标签页
  const removeTab = (targetKey: string) => {
    const newTabs = tabs.filter(tab => tab.key !== targetKey);
    setTabs(newTabs);
    
    if (activeTabKey === targetKey) {
      const newActiveKey = newTabs.length > 0 ? newTabs[newTabs.length - 1].key : '/dashboard';
      setActiveTabKey(newActiveKey);
      navigate(newActiveKey);
    }
  };

  // 关闭其他标签页
  const closeOtherTabs = () => {
    const currentTab = tabs.find(tab => tab.key === activeTabKey);
    const homeTabs = tabs.filter(tab => tab.key === '/dashboard');
    const newTabs = currentTab && currentTab.key !== '/dashboard' 
      ? [...homeTabs, currentTab] 
      : homeTabs;
    setTabs(newTabs);
  };

  // 关闭所有标签页
  const closeAllTabs = () => {
    const homeTabs = tabs.filter(tab => tab.key === '/dashboard');
    setTabs(homeTabs);
    setActiveTabKey('/dashboard');
    navigate('/dashboard');
  };

  // 刷新当前页面
  const refreshCurrentPage = () => {
    window.location.reload();
  };

  /**
   * 退出登录：调用认证服务登出并跳转登录页
   */
  const handleLogout = async () => {
    try {
      await AuthService.logout();
      message.success('已退出登录');
    } catch (e) {
      // ignore
    } finally {
      navigate('/login');
    }
  };

  const handleMenuClick: MenuProps['onClick'] = (e) => {
    const menuItem = findMenuItemByKey(e.key, menuItems);
    const label = menuItem?.label || e.key;
    const icon = menuItem?.icon;
    addTab(e.key, label as string, icon);
  };

  // 递归查找菜单项
  const findMenuItemByKey = (key: string, items: any[]): any => {
    for (const item of items) {
      if (item.key === key) {
        return item;
      }
      if (item.children) {
        const found = findMenuItemByKey(key, item.children);
        if (found) return found;
      }
    }
    return null;
  };

  useEffect(() => {
    const pathname = location.pathname === '/' ? '/dashboard' : location.pathname;
    const menuItem = findMenuItemByKey(pathname, menuItems);
    if (!menuItem) return;

    setTabs(prev => {
      if (prev.some(tab => tab.key === pathname)) return prev;
      return [
        ...prev,
        {
          key: pathname,
          label: menuItem.label as string,
          icon: menuItem.icon,
          closable: pathname !== '/dashboard',
        },
      ];
    });
    setActiveTabKey(pathname);
    setOpenKeys(getOpenKeys(pathname));
  }, [location.pathname]);

  // 获取当前选中的菜单项
  const getSelectedKeys = () => {
    return [location.pathname === '/' ? '/dashboard' : location.pathname];
  };

  // 获取当前展开的菜单项
  const getOpenKeys = (currentPath = location.pathname) => {
    const path = currentPath === '/' ? '/dashboard' : currentPath;
    const openKeys: string[] = [];
    
    if (path.startsWith('/dashboard') || path.startsWith('/settings')) {
      openKeys.push('pages');
    }
    if (path.startsWith('/users')) {
      openKeys.push('users');
    }
    if (path.startsWith('/orders')) {
      openKeys.push('orders');
    }
    if (path.startsWith('/packages')) {
      openKeys.push('packages');
    }
    if (path.startsWith('/functions')) {
      openKeys.push('functions');
      if (path.includes('/copywriting')) {
        openKeys.push('copywriting');
      }
      if (path.includes('/review')) {
        openKeys.push('review'); // review现在是顶级，这里保留兼容或移除均可，但为了展开需要看逻辑
      }
      if (path.includes('/workspace')) {
        openKeys.push('workspace'); // 这里保持不变，但因为workspace不再是父级菜单key，实际上不会影响展开，除非我们想让functions保持展开
      }
    }
    if (path.startsWith('/geo')) {
      openKeys.push('geo');
    }
    // 处理文案审核的自动展开
    if (path.includes('/functions/review')) {
       openKeys.push('review');
    }
    if (path.startsWith('/market')) {
      openKeys.push('market');
      if (path.includes('/agents')) {
        openKeys.push('agents');
      }
      if (path.includes('/recruitment')) {
        openKeys.push('recruitment');
      }
      if (path.includes('/operations')) {
        openKeys.push('operations');
      }
      if (path.includes('/partners')) {
        openKeys.push('partners');
      }
    }
    if (path.startsWith('/finance')) {
      openKeys.push('finance');
      if (path.includes('/market-expenses')) {
        openKeys.push('market-expenses');
      }
    }
    if (path.startsWith('/accounts')) {
      openKeys.push('accounts');
    }
    if (path.startsWith('/system')) {
      openKeys.push('system');
    }
    if (path.startsWith('/logs')) {
      openKeys.push('logs');
    }
    
    return openKeys;
  };

  return (
    <AntLayout style={{ minHeight: '100vh', overflowY: 'auto' }}>
      {/* 严格按照参考网站的div结构 */}
      <Sider
        trigger={null}
        collapsible
        collapsed={collapsed}
        style={{
          overflow: 'auto',
          height: '100vh',
          position: 'fixed',
          left: 0,
          top: 0,
          bottom: 0,
        }}
      >
        <div className="demo-logo-vertical" style={{ 
          height: 64, 
          margin: 16, 
          background: 'rgba(255, 255, 255, 0.3)',
          borderRadius: 6,
          display: 'flex',
          alignItems: 'center',
          justifyContent: 'center',
          color: 'white',
          fontSize: '16px',
          fontWeight: 'bold'
        }}>
          {collapsed ? '数医' : '数字医生管理后台'}
        </div>
        
        {/* 对应参考网站的 layui-side-scroll layui-left-menu */}
        <div className="layui-side-scroll layui-left-menu" style={{ height: 'calc(100vh - 96px)', overflowY: 'auto' }}>
          {/* 对应参考网站的 layui-nav layui-nav-tree layui-left-nav-tree layui-this */}
          <Menu
            theme="dark"
            mode="inline"
            selectedKeys={getSelectedKeys()}
            openKeys={openKeys}
            onOpenChange={(keys) => setOpenKeys(keys as string[])}
            items={visibleMenuItems}
            onClick={handleMenuClick}
            style={{
              border: 'none',
              background: 'transparent'
            }}
          />
        </div>
      </Sider>
      
      <AntLayout style={{ marginLeft: collapsed ? 80 : 200 }}>
        <Header
          style={{
            padding: '0 16px',
            background: colorBgContainer,
            display: 'flex',
            alignItems: 'center',
            justifyContent: 'space-between',
            borderBottom: '1px solid #f0f0f0',
          }}
        >
          <div style={{ display: 'flex', alignItems: 'center' }}>
            <Button
              type="text"
              icon={collapsed ? <MenuUnfoldOutlined /> : <MenuFoldOutlined />}
              onClick={() => setCollapsed(!collapsed)}
              style={{
                fontSize: '16px',
                width: 64,
                height: 64,
              }}
            />
            <div style={{ 
              fontSize: '18px', 
              fontWeight: 'bold', 
              color: '#1890ff',
              marginLeft: '8px'
            }}>
              数字医生管理后台
            </div>
          </div>
          
          <div style={{ display: 'flex', alignItems: 'center', gap: '8px' }}>
            {accessContext.isSuperAdmin && <ModelFailureNotices />}
            <Button
              type="text"
              icon={<ReloadOutlined />}
              onClick={refreshCurrentPage}
              title="刷新"
            />
            <Button
              type="text"
              icon={<DeleteOutlined />}
              onClick={closeAllTabs}
              title="清理"
            />
            <div style={{ display: 'flex', alignItems: 'center' }}>
              <Dropdown
                menu={{
                  items: [
                    {
                      key: 'closeOther',
                      label: '关闭其他',
                      icon: <EllipsisOutlined />,
                      onClick: closeOtherTabs,
                    },
                    {
                      key: 'closeAll',
                      label: '关闭全部',
                      icon: <CloseOutlined />,
                      onClick: closeAllTabs,
                    },
                  ],
                }}
              >
                <Button type="text" icon={<EllipsisOutlined />} />
              </Dropdown>
            </div>
            <Dropdown
              menu={{
                items: [
                  {
                    key: 'logout',
                    label: '退出登录',
                    icon: <LogoutOutlined />,
                    onClick: handleLogout,
                  },
                ],
              }}
            >
              <Button type="text">
                <Space>
                  <UserOutlined />
                  管理员
                  <DownOutlined />
                </Space>
              </Button>
            </Dropdown>
          </div>
        </Header>
        
        <Content
          style={{
            margin: 0,
            padding: 0,
            minHeight: 280,
            background: '#f5f5f5',
            overflowY: 'auto',
          }}
        >
          <Tabs
            type="editable-card"
            activeKey={activeTabKey}
            onChange={(key) => {
              setActiveTabKey(key);
              navigate(key);
            }}
            onEdit={(targetKey, action) => {
              if (action === 'remove') {
                removeTab(targetKey as string);
              }
            }}
            items={tabs.map(tab => ({
              key: tab.key,
              label: (
                <span>
                  {tab.icon}
                  <span style={{ marginLeft: tab.icon ? 8 : 0 }}>{tab.label}</span>
                </span>
              ),
              children: (
                <div
                  style={{
                    padding: 0,
                    height: '100%', // 改为height: '100%'而不是minHeight: '100vh'
                    background: 'transparent',
                  }}
                >
                  {children}
                </div>
              ),
              closable: tab.closable,
            }))}
            style={{
              margin: 0,
              background: '#fff',
            }}
            tabBarStyle={{
              margin: 0,
              padding: '0 16px',
              background: '#fff',
              borderBottom: '1px solid #f0f0f0',
            }}
          />
        </Content>
      </AntLayout>
    </AntLayout>
  );
};

export default Layout;
