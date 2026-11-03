export interface RawRoleIdentity {
  id?: number | string | null;
  roleId?: number | string | null;
}

export interface RoleIdentity {
  /**
   * Backward-compatible selection value used by existing role-assignment UI.
   * Never use this alias for role-management CRUD requests.
   * @deprecated Prefer roleId for assignments and databaseId for management.
   */
  id: string;
  /** t_role.id: the database primary key required by admin role CRUD APIs. */
  databaseId?: string;
  /** t_role.role_id: the business role identifier used by user-role mappings. */
  roleId?: string;
}

const stringifyId = (value: number | string | null | undefined): string | undefined =>
  value === null || typeof value === 'undefined' ? undefined : String(value);

/**
 * Keeps database and business identifiers distinct while preserving the
 * historical `id` alias for callers that use a role in an assignment field.
 */
export const mapRoleIdentity = (raw: RawRoleIdentity): RoleIdentity => {
  const databaseId = stringifyId(raw.id);
  const roleId = stringifyId(raw.roleId);
  return {
    id: roleId ?? databaseId ?? '',
    databaseId,
    roleId,
  };
};

export const requireDatabaseRoleId = (databaseId: unknown): string => {
  const normalized = String(databaseId ?? '').trim();
  if (!/^\d+$/.test(normalized)) {
    throw new Error('角色数据缺少有效的数据库主键，已停止操作');
  }
  return normalized;
};
