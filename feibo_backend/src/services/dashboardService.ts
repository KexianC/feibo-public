import { authenticatedRequest } from './auth';

export interface UserOverview {
  totalUsers?: number;
  activeUsers?: number;
  todayNewUsers?: number;
}

export interface RegionAgentItem {
  regionName: string;
  agentCount: number;
  totalPerformance?: number;
}

export interface RechargePoint {
  time: string;
  value: number;
}

export interface VideoRankItem {
  nick_name: string;
  total_duration: number;
}

export interface DashboardDateRange { startDate?: string; endDate?: string }
export interface LoginSummary {
  total: number; failed: number;
  ranking: { name: string; total: number; failed: number; success: number }[];
}
export interface AuditSummary { total: number; pending: number; passed: number; rejected: number }

const rangeQuery = (range: DashboardDateRange) => {
  const query = new URLSearchParams();
  if (range.startDate) query.set('startDate', range.startDate);
  if (range.endDate) query.set('endDate', range.endDate);
  return query.toString();
};

class DashboardService {
  private static instance: DashboardService;
  private baseUrl = '/api/admin/dashboard';

  static getInstance(): DashboardService {
    if (!DashboardService.instance) DashboardService.instance = new DashboardService();
    return DashboardService.instance;
  }

  /**
   * 获取用户总览数据
   */
  async getUserOverview(): Promise<UserOverview> {
    const resp = await authenticatedRequest<any>(`${this.baseUrl}/user-overview`, { method: 'GET' });
    const d = (resp as any)?.data ?? (resp as any);
    return {
      totalUsers: d?.totalUsers ?? d?.registeredUsers ?? 0,
      activeUsers: d?.activeUsers ?? 0,
      todayNewUsers: d?.todayNewUsers ?? 0,
    };
  }

  /**
   * 获取区域代理数据
   */
  async getRegionAgentData(): Promise<RegionAgentItem[]> {
    const resp = await authenticatedRequest<any>(`${this.baseUrl}/region-agent-data`, { method: 'GET' });
    const d = (resp as any)?.data ?? (resp as any);
    const list = Array.isArray(d) ? d : (d?.records || []);
    return list.map((it: any): RegionAgentItem => ({
      regionName: it?.regionName ?? '-',
      agentCount: Number(it?.agentCount ?? 0),
      totalPerformance: typeof it?.totalPerformance !== 'undefined' ? Number(it?.totalPerformance) : undefined,
    }));
  }

  /**
   * 获取充值金额统计（趋势点）
   */
  async getRechargeStatistics(): Promise<{ today: number; month: number; points: RechargePoint[] }> {
    const resp = await authenticatedRequest<any>(`${this.baseUrl}/recharge-statistics`, { method: 'GET' });
    const d = (resp as any)?.data ?? (resp as any);
    const arr = Array.isArray(d) ? d : (d?.recent7DaysRechargeTrend || d?.points || d?.records || []);
    const points = arr.map((it: any) => ({
      time: String(it?.time ?? it?.date ?? it?.day ?? ''),
      value: Number(it?.value ?? it?.amount ?? it?.total ?? 0),
    }));
    const today = Number(d?.todayRechargeTotal ?? 0);
    const month = Number(d?.monthRechargeTotal ?? 0);
    return { today, month, points };
  }

  /**
   * 获取实时订单监控数据（返回订单数）
   */
  async getRealTimeOrders(): Promise<{ count: number; latestOrders: any[] }> {
    const resp = await authenticatedRequest<any>(`${this.baseUrl}/real-time-orders`, { method: 'GET' });
    const d = (resp as any)?.data ?? (resp as any);
    const count = Number(d?.recent5MinutesOrderCount ?? d?.count ?? 0);
    const latestOrders = Array.isArray(d?.latestOrders) ? d.latestOrders : [];
    return { count, latestOrders };
  }

  /**
   * 获取合成时长排行
   */
  async getVideoRanking(range: DashboardDateRange = {}): Promise<VideoRankItem[]> {
    const resp = await authenticatedRequest<any>(`/api/admin/user/digital-works/video-ranking?${rangeQuery(range)}`, { method: 'GET' });
    const d = (resp as any)?.data ?? (resp as any);
    const list = Array.isArray(d) ? d : (d?.records || []);
    return list.map((it: any) => ({
      nick_name: it?.nick_name ?? it?.name ?? '-',
      total_duration: Number(it?.total_duration ?? 0),
    }));
  }

  async getLoginSummary(range: DashboardDateRange): Promise<LoginSummary> {
    const response = await authenticatedRequest<LoginSummary>(`${this.baseUrl}/login-summary?${rangeQuery(range)}`, { method: 'GET' });
    return response.data;
  }

  async getAuditSummary(range: DashboardDateRange): Promise<AuditSummary> {
    const response = await authenticatedRequest<AuditSummary>(`${this.baseUrl}/audit-summary?${rangeQuery(range)}`, { method: 'GET' });
    return response.data;
  }

  async getConversationSummary(range: DashboardDateRange): Promise<{ total: number }> {
    const response = await authenticatedRequest<{ total: number }>(`${this.baseUrl}/conversation-summary?${rangeQuery(range)}`, { method: 'GET' });
    return response.data;
  }
}

export default DashboardService.getInstance();
