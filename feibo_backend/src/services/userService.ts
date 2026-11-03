import { authenticatedRequest } from './auth';

// 用户信息接口
export interface User {
  id: number;
  userId: number;
  nickName: string;
  avatar?: string;
  phoneNumber: string;
  gender: number;
  status: boolean;
  openId?: string;
  createTime: string;
  updateTime: string;
}

// 分页查询参数
export interface UserPageParams {
  page: number;
  size: number;
  keyword?: string;
  status?: string;
  startDate?: string;
  endDate?: string;
  oderByUpdate?: number;
  oderByCreate?: number;
}

// 分页响应
export interface PageResponse<T> {
  records: T[];
  total: number;
  pages: number;
  size: number;
  current: number;
}

// 用户更新参数
export interface UpdateUserParams {
  username?: string;
  email?: string;
  phoneNumber?: string;
  nickName?: string;
  avatar?: string;
}

// AI模型信息
export interface AIModel {
  id: string;
  name: string;
  description?: string;
  version?: string;
  status: 'ACTIVE' | 'INACTIVE';
  createdAt?: string;
}

export class UserService {
  private static instance: UserService;

  private constructor() {
    // 私有构造函数
  }

  public static getInstance(): UserService {
    if (!UserService.instance) {
      UserService.instance = new UserService();
    }
    return UserService.instance;
  }

  /**
   * 创建用户（仅昵称和手机号）
   * - 采用通用认证请求与响应格式（与其他服务一致）
   * - 请求体为 JSON：{ nickName, phoneNumber }
   * - 响应按 `ApiResponse<User>` 解析，返回 `data`
   * @param params 包含 `nickName` 与 `phoneNumber` 的创建参数
   * @returns 创建成功后的用户信息
   */
  async createUser(params: { nickName: string; phoneNumber: string }): Promise<User> {
    try {
      const resp = await authenticatedRequest<User>(
        '/api/admin/user/users/create',
        {
          method: 'POST',
          body: JSON.stringify({
            nickName: params.nickName,
            phoneNumber: params.phoneNumber,
          }),
        }
      );
      // 统一返回解析：通用认证函数返回 `{ code, msg, data }`
      return resp.data || (resp as any);
    } catch (error) {
      console.error('创建用户失败:', error);
      throw error;
    }
  }

  /**
   * 分页查询用户列表
   */
  async getUsersPage(params: UserPageParams): Promise<PageResponse<User>> {
    const queryParams = new URLSearchParams();
    queryParams.append('page', params.page.toString());
    queryParams.append('size', params.size.toString());
    
    if (params.keyword) {
      queryParams.append('keyword', params.keyword);
    }
    if (params.status) {
      queryParams.append('status', params.status);
    }
    if (params.startDate) {
      queryParams.append('startDate', params.startDate);
    }
    if (params.endDate) {
      queryParams.append('endDate', params.endDate);
    }
    if (typeof params.oderByUpdate !== 'undefined') {
      queryParams.append('oderByUpdate', String(params.oderByUpdate));
    }
    if (typeof params.oderByCreate !== 'undefined') {
      queryParams.append('oderByCreate', String(params.oderByCreate));
    }

    try {
      const response = await authenticatedRequest<any>(
        `/api/admin/user/users/page?${queryParams.toString()}`,
        {
          method: 'GET',
        }
      );
      // API返回的数据结构：{ data: { records: [], total: number, size: number, current: number, pages: number } }
      return response.data;
    } catch (error) {
      console.error('获取用户列表失败:', error);
      throw error;
    }
  }

  /**
   * 根据ID查询用户详情
   */
  async getUserById(id: string): Promise<User> {
    try {
      const response = await authenticatedRequest<any>(
        `/api/admin/user/users/${id}`,
        {
          method: 'GET',
        }
      );
      return response.data?.data ?? response.data;
    } catch (error) {
      console.error('获取用户详情失败:', error);
      throw error;
    }
  }

  /**
   * 更新用户信息
   */
  async updateUser(id: string, params: UpdateUserParams): Promise<User> {
    try {
      const response = await authenticatedRequest<User>(
        `/api/admin/user/users/${id}`,
        {
          method: 'PUT',
          body: JSON.stringify(params),
        }
      );
      return response.data;
    } catch (error) {
      console.error('更新用户信息失败:', error);
      throw error;
    }
  }

  /**
   * 更新用户状态（启用/禁用）
   */
  async updateStatus(id: string, status: 'ACTIVE' | 'DISABLED'): Promise<void> {
    try {
      await authenticatedRequest<void>(
        `/api/admin/user/users/status/${id}`,
        {
          method: 'PUT',
          body: JSON.stringify({ disabled: status === 'DISABLED' }),
        }
      );
    } catch (error) {
      console.error('更新用户状态失败:', error);
      throw error;
    }
  }

  async createUsersByExcel(file: File): Promise<string[]> {
    const form = new FormData();
    form.append('file', file);
    const response = await authenticatedRequest<string[]>(
      '/api/admin/user/users/creatByExcel',
      {
        method: 'POST',
        body: form,
      }
    );
    if (!Array.isArray(response.data)) throw new Error('导入结果响应不完整，请刷新用户列表核对');
    return response.data;
  }

  /**
   * 获取可用的AI模型列表
   */
  async getAvailableModels(): Promise<AIModel[]> {
    try {
      const response = await authenticatedRequest<AIModel[]>(
        '/api/admin/user/users/models',
        {
          method: 'GET',
        }
      );
      return response.data;
    } catch (error) {
      console.error('获取AI模型列表失败:', error);
      throw error;
    }
  }

  /**
   * 格式化用户状态显示
   */
  formatUserStatus(status: string): string {
    switch (status) {
      case 'ACTIVE':
        return '正常';
      case 'INACTIVE':
        return '未激活';
      case 'DISABLED':
        return '已禁用';
      default:
        return status;
    }
  }

  /**
   * 获取状态对应的颜色
   */
  getStatusColor(status: string): string {
    switch (status) {
      case 'ACTIVE':
        return 'text-green-600';
      case 'INACTIVE':
        return 'text-yellow-600';
      case 'DISABLED':
        return 'text-red-600';
      default:
        return 'text-gray-600';
    }
  }

  /**
   * 格式化日期时间
   */
  formatDateTime(dateTime?: string): string {
    if (!dateTime) return '-';
    try {
      return new Date(dateTime).toLocaleString('zh-CN');
    } catch {
      return dateTime;
    }
  }
}

export default UserService.getInstance();
