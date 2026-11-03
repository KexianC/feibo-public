import { authenticatedRequest } from './auth';

// 通用分页响应结构
export interface PageResponse<T> {
  records: T[];
  total: number;
  pages: number;
  size: number;
  current: number;
}

// 操作日志条目接口（与后端返回字段对齐）
export interface OperationLog {
  logId: string;          // 日志ID
  operator?: string;      // 操作用户
  nickName?: string;      // 用户昵称
  operatorIp?: string;    // 操作IP
  loginRegion?: string;   // 登录地区
  loginDevice?: string;   // 登录设备信息（UA）
  operationMethod?: string; // 请求方法
  operationDesc?: string; // 操作描述（含成功/失败与耗时）
  operationTime?: string; // 操作时间
  status?: number;        // 派生状态：1成功/2失败/0未知
}

// 登录日志条目接口（与后端返回字段对齐）
export interface LoginLog {
  logId: string;           // 日志ID（字符串保证安全显示）
  loginUser?: string;      // 登录用户
  nickName?: string;       // 用户昵称
  loginIp?: string;        // 登录IP
  loginRegion?: string;    // 登录地区
  loginUserAgent?: string; // 登录设备信息（UA）
  status?: number;         // 派生状态：1成功/2失败/0未知
  loginTime?: string;      // 登录时间
}

// 页面使用的通用分页查询参数（接受 page/size，并在服务内转换为 pageNum/pageSize）
export interface LogPageParams {
  page: number;
  size: number;
  username?: string;      // 登录日志沿用
  operator?: string;      // 操作日志使用
  status?: number;        // 数值枚举
  operation?: string;     // 旧字段兼容
  operationDesc?: string; // 旧字段兼容（即将废弃）
  operationMethod?: string; // 操作方法搜索（login 等）
  startTime?: string;     // 开始时间（yyyy-MM-dd HH:mm:ss）
  endTime?: string;       // 结束时间（yyyy-MM-dd HH:mm:ss）
  startDate?: string;     // 登录日志：开始日期（yyyy-MM-dd）
  endDate?: string;       // 登录日志：结束日期（yyyy-MM-dd）
}

export class LogService {
  private static instance: LogService;

  private constructor() {}

  /**
   * 获取单例实例
   */
  public static getInstance(): LogService {
    if (!LogService.instance) {
      LogService.instance = new LogService();
    }
    return LogService.instance;
  }

  /**
   * 分页查询操作日志
   * @param params LogPageParams 查询参数（page/size）
   * @returns PageResponse<OperationLog>
   */
  async getOperationLogsPage(params: LogPageParams): Promise<PageResponse<OperationLog>> {
    const qs = new URLSearchParams();
    // 对齐接口文档：分页参数为 page/size
    qs.append('page', String(params.page));
    qs.append('size', String(params.size));
    // 支持 operator 或 username，两者择一
    const operator = params.operator ?? params.username;
    if (operator) qs.append('operator', operator);
    // 支持 operationMethod（优先），兼容旧字段 operationDesc/operation
    const operationMethod = params.operationMethod ?? params.operationDesc ?? params.operation;
    if (operationMethod) qs.append('operationMethod', operationMethod);
    // 时间范围：startTime/endTime（yyyy-MM-dd HH:mm:ss）
    if (params.startTime) qs.append('startTime', params.startTime);
    if (params.endTime) qs.append('endTime', params.endTime);
    // 注意：接口文档未声明状态筛选参数，这里不再透传 status，以避免歧义

    const response = await authenticatedRequest<any>(
      `/api/admin/log/operation-logs/page?${qs.toString()}`,
      { method: 'GET' }
    );
    const data = response.data as PageResponse<any>;
    if (Array.isArray(data.records)) {
      data.records = data.records.map((item: any) => {
        const desc: string = item.operationDesc ?? '';
        const rawNickName = item.nickName ?? item.nickname;
        let status = 0;
        if (desc.includes('成功')) status = 1;
        else if (desc.includes('失败')) status = 2;
        return {
          logId: String(item.logId ?? item.id),
          operator: item.operator ?? item.username,
          nickName: typeof rawNickName === 'string' ? rawNickName.trim() || undefined : undefined,
          operatorIp: item.operatorIp ?? item.ip,
          loginRegion: item.loginRegion,
          loginDevice: item.loginDevice ?? item.userAgent,
          operationMethod: item.operationMethod ?? item.method,
          operationDesc: desc || item.operation,
          operationTime: item.operationTime ?? item.createTime,
          status,
        } as OperationLog;
      });
    }
    return data as PageResponse<OperationLog>;
  }

  /**
   * 分页查询登录日志
   * @param params LogPageParams 查询参数（page/size），用户名与时间范围等
   * @returns PageResponse<LoginLog>
   *
   * 说明：
   * - 页面参数在这里转换为后端实际接收的
   *   pageNum/pageSize/loginUser/loginStatus/startTime/endTime。
   * - 响应字段根据新的返回结构进行适配（logId/loginUser/loginIp/loginRegion/loginUserAgent/loginStatus/loginTime）。
   * - 将字符串 loginStatus（如“登录成功”）派生为数值状态：1成功/2失败/0未知。
   */
  async getLoginLogsPage(params: LogPageParams): Promise<PageResponse<LoginLog>> {
    const qs = new URLSearchParams();
    qs.append('pageNum', String(params.page));
    qs.append('pageSize', String(params.size));
    if (params.username) qs.append('loginUser', params.username);

    const loginStatus = params.status === 1
      ? '登录成功'
      : params.status === 2
        ? '登录失败'
        : undefined;
    if (loginStatus) qs.append('loginStatus', loginStatus);

    const startTime = params.startTime || (params.startDate ? `${params.startDate} 00:00:00` : undefined);
    const endTime = params.endTime || (params.endDate ? `${params.endDate} 23:59:59` : undefined);
    if (startTime) qs.append('startTime', startTime);
    if (endTime) qs.append('endTime', endTime);

    const response = await authenticatedRequest<any>(
      `/api/admin/log/login-logs/page?${qs.toString()}`,
      { method: 'GET' }
    );
    const data = response.data as PageResponse<any>;
    if (Array.isArray(data.records)) {
      data.records = data.records.map((item: any) => {
        // 从字符串状态派生数值：包含“成功”记为1，包含“失败”记为2，其余0
        const statusText: string = String(item.loginStatus ?? item.status ?? '');
        const rawNickName = item.nickName ?? item.nickname;
        let status = 0;
        if (statusText.includes('成功')) status = 1;
        else if (statusText.includes('失败')) status = 2;
        return {
          logId: String(item.logId ?? item.id),
          loginUser: item.loginUser ?? item.username,
          nickName: typeof rawNickName === 'string' ? rawNickName.trim() || undefined : undefined,
          loginIp: item.loginIp ?? item.ip,
          loginRegion: item.loginRegion,
          loginUserAgent: item.loginUserAgent ?? item.userAgent ?? item.deviceInfo,
          loginTime: item.loginTime ?? item.createTime,
          status,
        } as LoginLog;
      });
    }
    return data as PageResponse<LoginLog>;
  }

  /**
   * 格式化日期时间
   * @param dateTime 字符串日期时间
   * @returns 格式化后的中文本地时间
   */
  formatDateTime(dateTime?: string): string {
    if (!dateTime) return '-';
    try {
      const date = new Date(dateTime);
      return date.toLocaleString('zh-CN', {
        year: 'numeric', month: '2-digit', day: '2-digit',
        hour: '2-digit', minute: '2-digit', second: '2-digit'
      });
    } catch (e) {
      return dateTime;
    }
  }

  /**
   * 获取状态标签配置
   * @param status 数值状态
   * @param type 日志类型（operation/login）
   * @returns { text, color }
   */
  getStatusConfig(status: number, type: 'operation' | 'login'): { text: string; color: string } {
    // 默认映射：0未知、1成功、2失败、3处理中
    const map: Record<number, { text: string; color: string }> = {
      0: { text: '未知', color: 'default' },
      1: { text: '成功', color: 'green' },
      2: { text: '失败', color: 'red' },
      3: { text: '处理中', color: 'blue' },
    };
    return map[Number(status)] || map[0];
  }
}

export default LogService.getInstance();
