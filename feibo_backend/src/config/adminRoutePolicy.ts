export type AdminRouteRequirement = 'admin' | 'super-admin' | 'disabled';

export interface AdminAccessContext {
  isAdmin: boolean;
  isSuperAdmin: boolean;
  permissionCodes: readonly string[];
}

const DISABLED_ROUTE_PREFIXES = [
  '/market', '/finance',
  // Retired demo pages stay denied for old bookmarks and nested URLs, including
  // super administrators. Real payment, OSS and copywriting APIs are unchanged.
  '/settings/site', '/settings/theme', '/system/payment', '/system/menu',
  '/system/storage/workbench', '/functions/copywriting/questions',
] as const;
const ADMIN_HOME_PATHS = new Set(['/', '/dashboard']);

interface CapabilityRoutePolicy {
  prefix: string;
  capability: string;
  superAdminOnly?: boolean;
}

// Ordered most-specific-first. The route guard and menu filtering both call
// this policy, preventing a hidden menu from being bypassed with a direct URL.
const CAPABILITY_ROUTE_POLICIES: readonly CapabilityRoutePolicy[] = [
  { prefix: '/geo', capability: 'admin.console', superAdminOnly: true },
  { prefix: '/users/manage', capability: 'user.manage', superAdminOnly: true },
  { prefix: '/orders', capability: 'finance.manage', superAdminOnly: true },
  { prefix: '/packages', capability: 'finance.manage', superAdminOnly: true },
  { prefix: '/accounts', capability: 'identity.manage', superAdminOnly: true },
  { prefix: '/system', capability: 'system.manage', superAdminOnly: true },
  { prefix: '/settings', capability: 'system.manage', superAdminOnly: true },
  { prefix: '/logs', capability: 'audit.read', superAdminOnly: true },
  { prefix: '/users', capability: 'user.read' },
  { prefix: '/functions', capability: 'content.manage' },
  { prefix: '/', capability: 'admin.console' },
] as const;

const normalizePathname = (pathname: string): string => {
  if (!pathname || pathname === '/') return '/';
  let decodedPathname = pathname;
  try {
    decodedPathname = decodeURIComponent(pathname);
  } catch {
    // A malformed escape must never weaken the policy. The raw path is still
    // normalized and evaluated below, while React Router will reject it.
  }
  return decodedPathname.trim().toLowerCase().replace(/\/+$/, '') || '/';
};

const matchesPathSegment = (pathname: string, prefix: string): boolean => (
  pathname === prefix || pathname.startsWith(`${prefix}/`)
);

export const getAdminRouteRequirement = (pathname: string): AdminRouteRequirement => {
  const normalizedPathname = normalizePathname(pathname);

  if (DISABLED_ROUTE_PREFIXES.some(prefix => matchesPathSegment(normalizedPathname, prefix))) {
    return 'disabled';
  }

  const policy = CAPABILITY_ROUTE_POLICIES.find(candidate => (
    candidate.prefix === '/' || matchesPathSegment(normalizedPathname, candidate.prefix)
  ));
  if (policy?.superAdminOnly) {
    return 'super-admin';
  }

  return 'admin';
};

export const canAccessAdminRoute = (
  pathname: string,
  context: AdminAccessContext,
): boolean => {
  const normalizedPathname = normalizePathname(pathname);
  const requirement = getAdminRouteRequirement(normalizedPathname);

  if (requirement === 'disabled') return false;
  // 后台首页是所有管理员的安全落点，不能依赖后来新增的 capability。
  // 否则旧会话缺少 permissions 字段时，会在“返回首页”处形成死循环。
  if (ADMIN_HOME_PATHS.has(normalizedPathname)) {
    return context.isAdmin || context.isSuperAdmin;
  }
  const policy = CAPABILITY_ROUTE_POLICIES.find(candidate => (
    candidate.prefix === '/' || matchesPathSegment(normalizedPathname, candidate.prefix)
  ));
  if (!policy) return false;
  // SUPER_ADMIN remains the hierarchy fallback even if a seed/migration is in
  // progress. Ordinary administrators fail closed when permissions are absent.
  if (context.isSuperAdmin) return true;
  if (policy.superAdminOnly || !context.isAdmin) return false;
  const capabilities = new Set(context.permissionCodes.map(code => code.trim().toLowerCase()));
  return capabilities.has(policy.capability);
};

export const readStoredAdminCapabilityCodes = (): string[] => {
  if (typeof localStorage === 'undefined') return [];
  try {
    const raw = localStorage.getItem('userInfo');
    if (!raw) return [];
    const parsed = JSON.parse(raw) as { permissions?: unknown };
    if (!Array.isArray(parsed.permissions)) return [];
    return parsed.permissions
      .filter((code): code is string => typeof code === 'string')
      .map(code => code.trim().toLowerCase())
      .filter(Boolean);
  } catch {
    return [];
  }
};
