import { authenticatedRequest } from './auth';

export interface UserRoleMap {
  id: string;
  userId: string;
  roleId: string;
  username?: string;
  roleName?: string;
  createTime?: string;
  updateTime?: string;
}

export interface UserRoleMapPageParams {
  pageNum: number;
  pagesize: number;
  nickName?: string;
  roleName?: string;
}

export interface PageResponse<T> {
  records: T[];
  total: number;
  pages: number;
  size: number;
  current: number;
}

class UserRoleMapService {
  private static instance: UserRoleMapService;

  static getInstance(): UserRoleMapService {
    if (!UserRoleMapService.instance) {
      UserRoleMapService.instance = new UserRoleMapService();
    }
    return UserRoleMapService.instance;
  }

  /**
   * 账户列表分页查询
   * 参数：pageNum/pagesize/nickName/roleName
   */
  async getPage(params: UserRoleMapPageParams): Promise<PageResponse<UserRoleMap>> {
    const qs = new URLSearchParams();
    qs.append('pageNum', String(params.pageNum));
    qs.append('pageSize', String(params.pagesize));
    if (params.nickName) qs.append('nickName', params.nickName);
    if (params.roleName) qs.append('roleName', params.roleName);
    const resp = await authenticatedRequest<PageResponse<any>>(`/api/admin/user-role-map/page?${qs.toString()}`, { method: 'GET' });
    const data = resp.data as PageResponse<any>;
    const records = (data.records || []).map((r: any): UserRoleMap => ({
      id: String(r.id ?? r.mapId ?? ''),
      userId: String(r.userId ?? ''),
      roleId: String(r.roleId ?? ''),
      username: r.username ?? r.nickName,
      roleName: r.roleName,
      createTime: r.createTime,
      updateTime: r.updateTime,
    }));
    return {
      records,
      total: data.total,
      pages: data.pages,
      size: data.size,
      current: data.current,
    };
  }

  async getAll(): Promise<UserRoleMap[]> {
    const resp = await authenticatedRequest<any[]>('/api/admin/user-role-map', { method: 'GET' });
    const list = (resp.data as any[]) || [];
    return list.map((r: any): UserRoleMap => ({
      id: String(r.id ?? r.mapId ?? ''),
      userId: String(r.userId ?? ''),
      roleId: String(r.roleId ?? ''),
      username: r.username ?? r.nickName,
      roleName: r.roleName,
      createTime: r.createTime,
      updateTime: r.updateTime,
    }));
  }

  async getById(id: string): Promise<UserRoleMap> {
    const resp = await authenticatedRequest<any>(`/api/admin/user-role-map/${id}`, { method: 'GET' });
    const r = resp.data;
    return {
      id: String(r.id ?? r.mapId ?? ''),
      userId: String(r.userId ?? ''),
      roleId: String(r.roleId ?? ''),
      username: r.username ?? r.nickName,
      roleName: r.roleName,
      createTime: r.createTime,
      updateTime: r.updateTime,
    };
  }

  async create(payload: { userId: string; roleId: string }): Promise<boolean> {
    const resp = await authenticatedRequest<boolean>('/api/admin/user-role-map', {
      method: 'POST',
      body: JSON.stringify(payload),
    });
    return resp.data ?? false;
  }

  async update(payload: { id: string; userId: string; roleId: string }): Promise<boolean> {
    const resp = await authenticatedRequest<boolean>('/api/admin/user-role-map', {
      method: 'PUT',
      body: JSON.stringify(payload),
    });
    return resp.data ?? false;
  }

  async deleteById(id: string): Promise<boolean> {
    const resp = await authenticatedRequest<boolean>(`/api/admin/user-role-map/${id}`, { method: 'DELETE' });
    return (resp as any)?.data ?? true;
  }

  async deleteBatch(ids: string[]): Promise<boolean> {
    const resp = await authenticatedRequest<boolean>('/api/admin/user-role-map/batch', {
      method: 'DELETE',
      body: JSON.stringify(ids),
    });
    return (resp as any)?.data ?? true;
  }
}

export default UserRoleMapService.getInstance();
