type RoleLike =
  | string
  | {
      name?: unknown;
      roleName?: unknown;
      code?: unknown;
      roleCode?: unknown;
      authority?: unknown;
    };

function normalizeRole(value: unknown) {
  return String(value ?? '').trim().toUpperCase();
}

function isAdminRole(role: RoleLike) {
  if (typeof role === 'string') {
    return normalizeRole(role) === 'ADMIN';
  }

  return [role.name, role.roleName, role.code, role.roleCode, role.authority].some(
    (value) => normalizeRole(value) === 'ADMIN',
  );
}

export function hasAdminRole(user: unknown): boolean {
  if (!user || typeof user !== 'object') {
    return false;
  }

  const userRecord = user as Record<string, unknown>;

  if (userRecord.isAdmin === true || userRecord.admin === true) {
    return true;
  }

  if (Array.isArray(userRecord.roles) && userRecord.roles.some(isAdminRole)) {
    return true;
  }

  return [userRecord.role, userRecord.roleName, userRecord.roleCode, userRecord.authority].some(
    (value) => normalizeRole(value) === 'ADMIN',
  );
}
