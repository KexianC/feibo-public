import { authenticatedRequest } from './auth';
import { mapRoleIdentity, requireDatabaseRoleId } from './roleIdentity';

/**
 * 角色接口定义
 * - 与后端角色管理文档对齐
 */
export interface Role {
  /** @deprecated 分配请使用 roleId，管理 CRUD 请使用 databaseId。 */
  id: string; // 兼容旧调用方的分配用ID：优先 roleId，不得用于角色管理CRUD
  databaseId?: string; // t_role.id：详情/更新/删除接口要求的数据库主键
  roleId?: string; // t_role.role_id：用户-角色映射使用的业务角色ID
  name: string; // 角色名称
  description?: string; // 角色描述
  status?: number | boolean; // 状态：可能为布尔或数值，统一原样保存（注意：当前返回值无此字段）
  createTime?: string; // 创建时间
  updateTime?: string; // 更新时间
}

/**
 * 后端原始角色结构（根据示例返回值）
 */
interface RawRole {
  id?: number | string;
  roleId?: number | string;
  name: string;
  description?: string;
  createTime?: string;
  updateTime?: string;
  // 可能存在其他字段，但示例中未包含 status
}

/**
 * 分页查询参数（与后端文档保持一致）
 */
export interface RolePageParams {
  pageNum?: number;
  pageSize?: number;
  current?: number; // 仅兼容旧调用方，发送时转换为 pageNum
  size?: number; // 仅兼容旧调用方，发送时转换为 pageSize
  name?: string; // 按名称过滤
  status?: number | boolean; // 按状态过滤（当前返回值无此字段，仅占位）
}

/**
 * 新增/更新角色参数
 */
export interface RoleParams {
  name: string;
  description?: string;
  status?: number | boolean;
}

export interface RoleUpdateParams extends RoleParams {
  databaseId: string;
}

/**
 * 权限信息接口
 */
export interface Permission {
  id: string; // 权限ID
  code: string; // 权限代码/唯一标识
  name: string; // 权限名称
  description?: string; // 权限描述
  reserved: boolean;
}

/**
 * 角色权限关联接口
 */
export interface RolePermissionSnapshot {
  roleDatabaseId: string;
  roleId: string;
  permissionIds: string[];
  version: string;
}

export interface RolePermissionUpdate {
  version: string;
  permissionIds: string[];
  confirmEmpty?: boolean;
}

/**
 * 通用分页响应结构
 */
export interface PageResponse<T> {
  records: T[];
  total: number;
  pages: number;
  size: number;
  current: number;
}

/**
 * 角色服务类：封装角色管理相关API调用
 */
export default class RoleService {
  private static normalizeRole(raw: RawRole): Role {
    return {
      ...mapRoleIdentity(raw),
      name: raw.name,
      description: raw.description,
      // 当前返回值未提供状态字段，保持为 undefined，便于前端兼容处理
      status: undefined,
      createTime: raw.createTime,
      updateTime: raw.updateTime,
    };
  }

  /**
   * 分页获取角色列表
   * @param params 分页与筛选参数
   */
  static async getRolesPage(params: RolePageParams): Promise<PageResponse<Role>> {
    // 使用统一的认证请求方法，兼容两种响应结构
    // 参数映射：后端契约使用 pageNum/pageSize。
    const current = params.current ?? params.pageNum ?? 1;
    const size = params.size ?? params.pageSize ?? 10;
    const requestParams: Record<string, string | number | boolean> = {
      pageNum: current,
      pageSize: size,
    };
    if (params.name) requestParams.name = params.name;
    if (typeof params.status !== 'undefined') requestParams.status = params.status;

    const qs = new URLSearchParams();
    Object.entries(requestParams).forEach(([k, v]) => { if (typeof v !== 'undefined' && v !== null) qs.append(k, String(v)); });
    const res = await authenticatedRequest<PageResponse<RawRole>>(`/api/admin/role/page?${qs.toString()}`, {
      method: 'GET',
    });

    const raw = res.data;
    const records: RawRole[] = raw?.records ?? [];

    const normalizedRecords: Role[] = records.map(RoleService.normalizeRole);

    return {
      records: normalizedRecords,
      total: raw?.total ?? normalizedRecords.length,
      pages: raw?.pages ?? 1,
      size: raw?.size ?? size,
      current: raw?.current ?? current,
    };
  }

  /**
   * 查询所有角色（非分页）
   */
  static async getAllRoles(): Promise<Role[]> {
    const res = await authenticatedRequest<RawRole[]>('/api/admin/role', {
      method: 'GET',
    });
    const list: RawRole[] = res.data ?? [];
    return list.map(RoleService.normalizeRole);
  }

  /**
   * 根据ID获取角色详情
   * @param id 角色ID
   */
  static async getRoleById(databaseId: string | number): Promise<Role> {
    const resolvedDatabaseId = requireDatabaseRoleId(databaseId);
    const res = await authenticatedRequest<RawRole>(`/api/admin/role/${encodeURIComponent(resolvedDatabaseId)}`, {
      method: 'GET',
    });
    const raw: RawRole = res.data;
    return RoleService.normalizeRole(raw);
  }

  /**
   * 创建新角色
   * @param payload 角色参数
   */
  static async createRole(payload: RoleParams): Promise<boolean> {
    const res = await authenticatedRequest<boolean>('/api/admin/role', {
      method: 'POST',
      body: JSON.stringify(payload),
    });
    return res.data ?? false;
  }

  /**
   * 更新角色信息
   * @param payload 角色参数，需包含名称等
   */
  static async updateRole(payload: RoleUpdateParams): Promise<boolean> {
    const { databaseId, ...roleFields } = payload;
    const res = await authenticatedRequest<boolean>('/api/admin/role', {
      method: 'PUT',
      body: JSON.stringify({
        id: requireDatabaseRoleId(databaseId),
        ...roleFields,
      }),
    });
    return res.data ?? false;
  }

  /**
   * 删除角色
   * @param id 角色ID
   */
  static async deleteRole(databaseId: string | number): Promise<boolean> {
    const resolvedDatabaseId = requireDatabaseRoleId(databaseId);
    const res = await authenticatedRequest<boolean>(`/api/admin/role/${encodeURIComponent(resolvedDatabaseId)}`, {
      method: 'DELETE',
    });
    return res.data ?? true;
  }

  /**
   * 批量删除角色
   * @param ids 角色ID数组
   */
  static async deleteRolesBatch(databaseIds: Array<string | number>): Promise<boolean> {
    const resolvedDatabaseIds = databaseIds.map(requireDatabaseRoleId);
    const res = await authenticatedRequest<boolean>('/api/admin/role/batch', {
      method: 'DELETE',
      body: JSON.stringify(resolvedDatabaseIds),
    });
    return res.data ?? true;
  }

  /**
   * 获取所有权限列表
   */
  static async getPermissions(): Promise<Permission[]> {
    const res = await authenticatedRequest<Permission[]>('/api/admin/permission', {
      method: 'GET',
    });
    return res.data ?? [];
  }

  /**
   * 更新角色权限
   * @param payload 角色权限关联参数
   */
  static async getRolePermissions(
    databaseId: string | number,
  ): Promise<RolePermissionSnapshot> {
    const resolvedDatabaseId = requireDatabaseRoleId(databaseId);
    const res = await authenticatedRequest<RolePermissionSnapshot>(
      `/api/admin/role/${encodeURIComponent(resolvedDatabaseId)}/permissions`,
      { method: 'GET' },
    );
    if (!res.data?.version || !Array.isArray(res.data.permissionIds)) {
      throw new Error('角色权限响应不完整，已停止编辑');
    }
    return res.data;
  }

  /**
   * 使用 GET 返回的版本全量替换角色权限；后端在版本过期时返回 HTTP 409。
   */
  static async updateRolePermissions(
    databaseId: string | number,
    payload: RolePermissionUpdate,
  ): Promise<RolePermissionSnapshot> {
    const resolvedDatabaseId = requireDatabaseRoleId(databaseId);
    const res = await authenticatedRequest<RolePermissionSnapshot>(
      `/api/admin/role/${encodeURIComponent(resolvedDatabaseId)}/permissions`, {
      method: 'PUT',
      body: JSON.stringify(payload),
    });
    if (!res.data?.version || !Array.isArray(res.data.permissionIds)) {
      throw new Error('角色权限更新响应不完整');
    }
    return res.data;
  }

  static isConflictError(error: unknown): boolean {
    if (!(error instanceof Error)) return false;
    const status = (error as Error & { status?: number }).status;
    return status === 409 || /\b409\b|并发|已被其他管理员更新/.test(error.message);
  }
}
