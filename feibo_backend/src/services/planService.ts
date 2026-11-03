import { authenticatedRequest } from './auth';

/**
 * 套餐（Plan）数据结构（规范化）
 * 说明：统一字段类型，并兼容旧版页面依赖的字段命名，避免页面改动过大。
 */
export interface Plan {
  id: string;            // 主键ID（字符串）
  name: string;          // 套餐名称（新：name；旧：packageName）
  description: string;   // 套餐描述（前端自填/后端若返回可映射）
  price: number;         // 价格（映射 price/amount）
  duration: number;      // 时长（天，映射 durationDays）
  status: number;        // 状态：0-启用，1-禁用（由 isActive 派生）
  createTime: string;    // 创建时间
  updateTime: string;    // 更新时间

  // 新增字段（来自新接口返回）
  aiMembershipPlanId?: string; // AI会员计划ID（字符串化）
  planCode?: string;           // 计划代码
  isActive?: boolean;          // 是否激活

  // 旧版字段保留（便于现有列展示）
  packageName?: string;
  category?: string;
  version?: string;
  sort?: number;
  chargeType?: string;
  durationText?: string;  // 例如 “365天” 或 “永久”
  amount?: number;
  isDeleted?: number;
}

/**
 * 分页查询参数（兼容旧版）：pageNum/pageSize/packageName/category/version
 * 如后端不支持 category/version，则忽略这两个参数。
 */
export interface PlanPageParams {
  pageNum: number;
  pageSize: number;
  packageName?: string;
  category?: string;
  version?: string;
}

/**
 * 新增/更新参数（根据页面表单，提交时映射为后端字段）
 */
export interface PlanParams {
  name: string;          // 映射为 packageName
  price: number;         // 映射为 amount
  duration: number;      // 若后端需要字符串时长，则 `${duration}天`
  description?: string;
  status: number;        // 可用于派生 isDeleted（如后端需要）
  features?: string[];
  category?: string;
  version?: string;
  chargeType?: string;
}

/**
 * 通用分页响应
 */
export interface PageResponse<T> {
  records: T[];
  total: number;
  current: number;
  size: number;
  pages: number;
}

/**
 * 计划（套餐）服务：分页查询、详情获取、新增、更新、删除
 * 现适配新返回结构字段：id/aiMembershipPlanId/planCode/name/price/durationDays/isActive。
 * 接口路径暂保持与现有实现一致，除非后端明确变化。
 */
export class PlanService {
  private static instance: PlanService;

  private constructor() {}

  /**
   * 获取单例实例
   */
  public static getInstance(): PlanService {
    if (!PlanService.instance) {
      PlanService.instance = new PlanService();
    }
    return PlanService.instance;
  }

  /**
   * 分页查询套餐列表
   * GET /api/admin/function/ai-membership-plan/page
   * 参数：pageNum/pageSize/packageName/category/version
   * 适配新字段：durationDays/price/isActive/name/planCode/aiMembershipPlanId
   */
  async getPlansPage(params: PlanPageParams): Promise<PageResponse<Plan>> {
    const qs = new URLSearchParams();
    qs.append('pageNum', String(params.pageNum));
    qs.append('pageSize', String(params.pageSize));
    if (params.packageName?.trim()) qs.append('planName', params.packageName.trim());

    const resp = await authenticatedRequest<PageResponse<any>>(
      `/api/admin/function/ai-membership-plan/page?${qs.toString()}`,
      { method: 'GET' }
    );
    const data = resp.data as PageResponse<any>;

    // 映射为前端使用的 Plan 结构（兼容旧/新返回字段）
    const normalized: PageResponse<Plan> = {
      records: (data.records || []).map((item: any): Plan => {
        // 新字段优先
        const durationDays = (() => {
          if (typeof item.durationDays === 'number') return item.durationDays;
          if (typeof item.duration === 'string') {
            const m = item.duration.match(/(\d+)/);
            return m ? Number(m[1]) : 0;
          }
          if (typeof item.timeliness === 'number') return item.timeliness;
          return 0;
        })();
        const priceVal = item.price ?? item.amount ?? 0;
        const nameVal = item.name ?? item.packageName ?? '';
        const isActive = typeof item.isActive === 'boolean' ? item.isActive : (Number(item.isDeleted) === 1 ? false : true);

        return {
          id: String(item.id),
          name: nameVal,
          description: '',
          price: Number(priceVal),
          duration: Number(durationDays),
          status: isActive ? 0 : 1, // 0-启用, 1-禁用
          createTime: item.createTime,
          updateTime: item.updateTime,
          // 新字段保留
          aiMembershipPlanId: item.aiMembershipPlanId !== undefined ? String(item.aiMembershipPlanId) : undefined,
          planCode: item.planCode,
          isActive,
          // 旧字段保留以兼容现有页面列
          packageName: nameVal,
          category: item.category,
          version: item.version,
          sort: item.sort,
          chargeType: item.chargeType,
          durationText: durationDays === -1 ? '永久' : (durationDays ? `${durationDays}天` : item.duration),
          amount: Number(priceVal),
          isDeleted: item.isDeleted,
        };
      }),
      total: data.total,
      current: data.current,
      size: data.size,
      pages: data.pages,
    };

    return normalized;
  }

  /**
   * 根据ID查询套餐详情
   * GET /api/admin/function/ai-membership-plan/{id}
   * 适配新字段映射。
   */
  async getPlanById(id: string): Promise<Plan> {
    const resp = await authenticatedRequest<any>(`/api/admin/function/ai-membership-plan/${id}`, { method: 'GET' });
    const item = resp.data;
    const durationDays = (() => {
      if (typeof item?.durationDays === 'number') return item.durationDays;
      if (typeof item?.duration === 'string') {
        const m = item.duration.match(/(\d+)/);
        return m ? Number(m[1]) : 0;
      }
      if (typeof item?.timeliness === 'number') return item.timeliness;
      return 0;
    })();
    const priceVal = item?.price ?? item?.amount ?? 0;
    const nameVal = item?.name ?? item?.packageName ?? '';
    const isActive = typeof item?.isActive === 'boolean' ? item.isActive : (Number(item?.isDeleted) === 1 ? false : true);

    return {
      id: String(item.id),
      name: nameVal,
      description: '',
      price: Number(priceVal),
      duration: Number(durationDays),
      status: isActive ? 0 : 1,
      createTime: item.createTime,
      updateTime: item.updateTime,
      aiMembershipPlanId: item.aiMembershipPlanId !== undefined ? String(item.aiMembershipPlanId) : undefined,
      planCode: item.planCode,
      isActive,
      packageName: nameVal,
      category: item.category,
      version: item.version,
      sort: item.sort,
      chargeType: item.chargeType,
      durationText: durationDays === -1 ? '永久' : (durationDays ? `${durationDays}天` : item.duration),
      amount: Number(priceVal),
      isDeleted: item.isDeleted,
    };
  }

  /**
   * 新增套餐
   * POST /api/admin/function/ai-membership-plan
   * 提交体映射兼容：
   * - 新字段：name/price/durationDays/isActive
   * - 旧字段：packageName/amount/duration
   */
  async createPlan(params: PlanParams): Promise<void> {
    const body = { name: params.name.trim(), price: params.price, durationDays: params.duration, isActive: params.status === 0 };
    const response = await authenticatedRequest<boolean>('/api/admin/function/ai-membership-plan', {
      method: 'POST',
      body: JSON.stringify(body)
    });
    if (response.data !== true) throw new Error('后端未创建会员套餐');
  }

  /**
   * 更新套餐
   * PUT /api/admin/function/ai-membership-plan
   * 提交体映射兼容新旧字段。
   */
  async updatePlan(id: string, params: PlanParams): Promise<void> {
    const body = { id, name: params.name.trim(), price: params.price, durationDays: params.duration, isActive: params.status === 0 };
    const response = await authenticatedRequest<boolean>('/api/admin/function/ai-membership-plan', {
      method: 'PUT',
      body: JSON.stringify(body)
    });
    if (response.data !== true) throw new Error('后端未更新会员套餐');
  }

  /**
   * 启用/禁用套餐
   * PUT /api/admin/function/ai-membership-plan/{id}/status
   * 约定：status=0 启用，status=1 禁用。
   */
  async updateStatus(id: string, status: number): Promise<void> {
    const body = { isActive: status === 0 };
    const response = await authenticatedRequest<boolean>(`/api/admin/function/ai-membership-plan/${id}/status`, {
      method: 'PUT',
      body: JSON.stringify(body)
    });
    if (response.data !== true) throw new Error('后端未更新套餐状态');
  }
}

export default PlanService.getInstance();
